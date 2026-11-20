import dev.monitorcidadao.buildlogic.CopyFixturesTask
import dev.monitorcidadao.buildlogic.contractsFixturesDir

plugins {
    alias(libs.plugins.monitor.jvm.library)
}

// Fixtures de contracts/ viram recursos (classpath "fixtures/*.json") — mesmas que o iOS usa.
val copyFixtures by tasks.registering(CopyFixturesTask::class) {
    fixturesDirectory.set(contractsFixturesDir)
    outputDirectory.set(layout.buildDirectory.dir("generated/fixtures/resources"))
}

sourceSets.named("main") {
    resources.srcDir(copyFixtures.flatMap { it.outputDirectory })
}

dependencies {
    api(project(":core:analytics"))
    api(project(":core:featureflags"))
    api(project(":domain"))

    api(platform(libs.junit.bom))
    api(libs.junit.jupiter.api)
    api(libs.kotlinx.coroutines.test)
    api(libs.turbine)
    api(libs.okhttp.mockwebserver)
    api(libs.okhttp.tls)
}
