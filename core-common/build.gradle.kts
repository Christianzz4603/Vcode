/*
 * Copyright Squircle CE contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
    id("com.vcode.feature")
    alias(libs.plugins.android.library)
    alias(libs.plugins.android.room)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.vcode.studio.core"

    room {
        schemaDirectory("$projectDir/schemas")
        generateKotlin = true
    }
    sourceSets {
        named("androidTest") {
            assets.srcDir("$projectDir/schemas")
        }
    }
    buildFeatures {
        compose = true
    }
}

// The library viewer and disassembler are written in Rust (native/disasm). They are built when cargo, the Rust
// targets for Android and the Android NDK are installed, and left out otherwise: the app still works, and the
// library viewer then says that it is not part of the build.
val nativeDir = rootProject.file("native/disasm")
val rustTargets = listOf(
    Triple("arm64-v8a", "aarch64-linux-android", "aarch64-linux-android26"),
    Triple("armeabi-v7a", "armv7-linux-androideabi", "armv7a-linux-androideabi26"),
    Triple("x86_64", "x86_64-linux-android", "x86_64-linux-android26"),
)

fun commandOutput(vararg command: String): String? {
    return try {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().readText()
        if (process.waitFor() == 0) text else null
    } catch (e: Exception) {
        null
    }
}

val ndkHome: String? = System.getenv("ANDROID_NDK_HOME") ?: System.getenv("ANDROID_NDK_ROOT")
val llvmBin: File? = ndkHome?.let { File(it, "toolchains/llvm/prebuilt/linux-x86_64/bin") }
val installedTargets: String? = if (llvmBin != null && llvmBin.isDirectory && nativeDir.isDirectory) {
    commandOutput("rustup", "target", "list", "--installed")
} else {
    null
}
val buildNative = llvmBin != null &&
    installedTargets != null &&
    rustTargets.all { installedTargets.contains(it.second) } &&
    commandOutput("cargo", "--version") != null

if (buildNative) {
    val bin = llvmBin!!
    val cargoHome = System.getenv("CARGO_HOME") ?: (System.getProperty("user.home") + "/.cargo")
    val hasLock = File(nativeDir, "Cargo.lock").isFile
    val outDir = layout.buildDirectory.dir("rustJniLibs")
    val copyTasks = rustTargets.map { (abi, triple, clang) ->
        val suffix = abi.filter { it.isLetterOrDigit() }
        val cargo = tasks.register<Exec>("cargoBuild$suffix") {
            workingDir = nativeDir
            commandLine(
                listOf("cargo", "build", "--release") + (if (hasLock) listOf("--locked") else emptyList()) +
                    listOf("--target", triple)
            )
            environment("CARGO_TARGET_" + triple.uppercase().replace('-', '_') + "_LINKER", File(bin, "$clang-clang").absolutePath)
            environment(
                "CARGO_ENCODED_RUSTFLAGS",
                listOf(
                    "-Clink-arg=-Wl,-z,max-page-size=16384",
                    "--remap-path-prefix=" + nativeDir.absolutePath + "=/native",
                    "--remap-path-prefix=" + cargoHome + "/registry/src=/cargo-registry",
                ).joinToString("\u001f")
            )
            inputs.files(fileTree(nativeDir) { include("Cargo.toml", "Cargo.lock", "src/**") })
            outputs.file(File(nativeDir, "target/$triple/release/libvcode_disasm.so"))
        }
        tasks.register<Copy>("copyRust$suffix") {
            dependsOn(cargo)
            from(File(nativeDir, "target/$triple/release/libvcode_disasm.so"))
            into(outDir.map { it.dir(abi) })
        }
    }
    android {
        sourceSets.getByName("main").jniLibs.srcDir(outDir)
    }
    tasks.named("preBuild") { dependsOn(copyTasks) }
    tasks.matching { it.name.startsWith("merge") && it.name.endsWith("JniLibFolders") }.configureEach {
        dependsOn(copyTasks)
    }
}

dependencies {

    api(project(":plugin-api"))
    implementation(project(":filesystems:filesystem-base"))
    implementation(libs.smali.assembler)
    implementation(libs.smali.baksmali)
    implementation(libs.smali.dexlib2)
    implementation(project(":core-ui"))

    implementation(libs.androidx.lifecycle.service)

    api(libs.androidx.core)
    api(libs.androidx.navigation.ui)
    api(libs.jakewharton.timber)
    api(libs.kotlinx.coroutines.android)
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization)
    api(libs.squareup.retrofit)
    api(libs.squareup.retrofit.converter)

    implementation(libs.androidx.room)
    ksp(libs.androidx.room.compiler)

    implementation(libs.google.dagger)
    ksp(libs.google.dagger.compiler)

    testImplementation(libs.test.junit)
    testImplementation(libs.test.mockk)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.test.junit.ext)
    androidTestImplementation(libs.test.runner)
}