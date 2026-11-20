plugins {
    alias(libs.plugins.monitor.jvm.library)
}

dependencies {
    testImplementation(project(":core:testing"))
}
