plugins {
    alias(libs.plugins.monitor.android.library.compose)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":domain"))
    api(libs.androidx.navigation3.runtime)
    implementation(libs.kotlinx.serialization.core)
}
