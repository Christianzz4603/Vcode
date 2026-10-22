plugins {
    id("com.vcode.feature")
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.vcode.studio.plugin.api"
}
