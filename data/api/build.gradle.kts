plugins {
    alias(libs.plugins.monitor.android.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":core:network"))
    testImplementation(project(":core:testing"))
}
