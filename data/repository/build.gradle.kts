plugins {
    alias(libs.plugins.monitor.android.library)
}

dependencies {
    api(project(":domain"))
    implementation(project(":data:api"))
    implementation(project(":core:database"))

    testImplementation(project(":core:testing"))
    // Rotas do api.md → arquivo de fixture (o mesmo roteador do servidor falso do Debug).
    testImplementation(project(":core:network-fixtures"))
    // Repositórios testados com Room real em memória + MockWebServer: Robolectric (JUnit 4 via Vintage).
    testImplementation(libs.robolectric)
    testImplementation(libs.junit4)
    testRuntimeOnly(libs.junit.vintage.engine)
}
