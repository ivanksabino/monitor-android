plugins {
    alias(libs.plugins.monitor.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.monitorcidadao.core.network.fixture"
}

// Servidor falso de fixtures: SÓ entra no app por `debugImplementation` (nada disto vai para o Release).
dependencies {
    api(project(":core:network"))

    testImplementation(project(":core:testing"))
}
