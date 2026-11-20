plugins {
    alias(libs.plugins.monitor.android.library)
}

dependencies {
    api(libs.androidx.datastore.preferences)
    api(libs.kotlinx.coroutines.core)

    testImplementation(project(":core:testing"))
}
