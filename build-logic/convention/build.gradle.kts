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

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    `kotlin-dsl`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    compileOnly(libs.plugin.android)
    compileOnly(libs.plugin.kotlin)
}

gradlePlugin {
    plugins {
        register("com.vcode.application") {
            id = "com.vcode.application"
            implementationClass = "com.vcode.studio.ApplicationModulePlugin"
        }
        register("com.vcode.test") {
            id = "com.vcode.test"
            implementationClass = "com.vcode.studio.TestModulePlugin"
        }
        register("com.vcode.feature") {
            id = "com.vcode.feature"
            implementationClass = "com.vcode.studio.FeatureModulePlugin"
        }
        register("com.vcode.kotlin") {
            id = "com.vcode.kotlin"
            implementationClass = "com.vcode.studio.KotlinModulePlugin"
        }
        register("com.vcode.publish") {
            id = "com.vcode.publish"
            implementationClass = "com.vcode.studio.PublishModulePlugin"
        }
        register("com.vcode.lint") {
            id = "com.vcode.lint"
            implementationClass = "com.vcode.studio.LintConventionPlugin"
        }
    }
}