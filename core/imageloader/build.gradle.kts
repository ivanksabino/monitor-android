plugins {
    alias(libs.plugins.monitor.android.library.compose)
}

android {
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    // implementation: o Coil não vaza para quem usa RemoteImage (arquitetura §6).
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp)

    // Prova na JVM de que as imagens saem pelo OkHttp injetado (mesmo cliente/pool do app).
    testImplementation(libs.robolectric)
    testImplementation(libs.junit4)
    testRuntimeOnly(libs.junit.vintage.engine)
}
