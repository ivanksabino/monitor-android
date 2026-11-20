plugins {
    alias(libs.plugins.monitor.jvm.library)
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    testImplementation(project(":core:testing"))
}
