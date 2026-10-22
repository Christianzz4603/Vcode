// Example of a Kotlin plugin for Vcode. It is built as an app only to get a classes.dex file,
// see .github/workflows/build_sample_plugin.yml and docs/PLUGINS.md.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.vcode.studio.sample"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.vcode.studio.sample"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }
}

dependencies {
    // Provided by Vcode at runtime, so it must not end up in the plugin
    compileOnly(project(":plugin-api"))
}
