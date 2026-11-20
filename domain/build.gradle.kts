plugins {
    alias(libs.plugins.monitor.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(libs.kotlinx.serialization.core)
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlinx.serialization.json)
    // Fakes e amostras compartilhados (o :core:testing depende do :domain só em main).
    testImplementation(project(":core:testing"))
}
