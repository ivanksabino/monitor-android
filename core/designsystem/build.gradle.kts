plugins {
    alias(libs.plugins.monitor.android.library.compose)
}

android {
    // Robolectric precisa dos recursos (strings, plurais, fontes) para testar labels de a11y e formatadores.
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    api(project(":core:imageloader"))
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.material3)

    // Testes de semântica (labels, papéis, estados) na JVM: Compose UI Test + Robolectric.
    // Robolectric só tem runner JUnit 4, que roda no JUnit Platform pelo motor Vintage,
    // lado a lado com os testes JUnit 5 puros do módulo.
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.junit4)
    testRuntimeOnly(libs.junit.vintage.engine)
    // Só no classpath de teste: em debugImplementation a ComponentActivity exportada do manifest
    // de teste vazava para o APK debug do :app.
    testImplementation(libs.androidx.compose.ui.test.manifest)
}
