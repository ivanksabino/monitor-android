plugins {
    alias(libs.plugins.monitor.android.feature)
}

android {
    // Testes de semântica da PerfilScreen (títulos, abas, estados, tags) precisam das strings.
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    // Compose UI Test + Robolectric na JVM. Robolectric só tem runner JUnit 4, que roda pelo motor
    // Vintage ao lado dos testes JUnit 5 dos ViewModels.
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.junit4)
    testRuntimeOnly(libs.junit.vintage.engine)
    testImplementation(libs.androidx.compose.ui.test.manifest)
}
