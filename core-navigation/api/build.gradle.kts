plugins {
    id("com.vcode.kotlin")
}

dependencies {

    api(libs.androidx.navigation.runtime)
    api(libs.kotlinx.serialization)

    compileOnly(libs.android.tools.platform)
}