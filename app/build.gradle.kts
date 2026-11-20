import com.android.build.api.artifact.SingleArtifact
import dev.monitorcidadao.buildlogic.CopyFixturesTask
import dev.monitorcidadao.buildlogic.VerifyNoFixturesTask
import dev.monitorcidadao.buildlogic.contractsFixturesDir

plugins {
    alias(libs.plugins.monitor.android.application)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.monitorcidadao.app"

    defaultConfig {
        applicationId = "dev.monitorcidadao.app"
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    // Testes da casca (AppFactory, abas, deep link) com Compose UI Test + Robolectric na JVM.
    testOptions.unitTests.isIncludeAndroidResources = true
}

// Fixtures da API entram SÓ no build Debug, como assets gerados a partir de contracts/fixtures.
val copyDebugFixtures by tasks.registering(CopyFixturesTask::class) {
    fixturesDirectory.set(contractsFixturesDir)
}

androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copyDebugFixtures, CopyFixturesTask::outputDirectory)
    }
    // Garante no artefato (não só no código-fonte) que o Release não leva fixtures nem o servidor falso.
    onVariants(selector().withBuildType("release")) { variant ->
        val verify = tasks.register<VerifyNoFixturesTask>("verify${variant.name.replaceFirstChar(Char::uppercase)}NoFixtures") {
            apkDirectory.set(variant.artifacts.get(SingleArtifact.APK))
            forbiddenPackages.set(listOf("dev/monitorcidadao/core/network/fixture/"))
            report.set(layout.buildDirectory.file("reports/${variant.name}-no-fixtures.txt"))
        }
        tasks.matching { it.name == "assemble${variant.name.replaceFirstChar(Char::uppercase)}" }
            .configureEach { finalizedBy(verify) }
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data:api"))
    implementation(project(":data:repository"))
    implementation(project(":core:routing"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:network"))
    // Servidor de fixtures: só Debug. O Release não leva nem as classes (ver verifyReleaseApk).
    debugImplementation(project(":core:network-fixtures"))
    implementation(project(":core:imageloader"))
    implementation(project(":core:storage"))
    implementation(project(":core:database"))
    implementation(project(":core:analytics"))
    implementation(project(":core:featureflags"))
    implementation(project(":feature:feed"))
    implementation(project(":feature:explorar"))
    implementation(project(":feature:perfil"))
    implementation(project(":feature:entenda"))
    implementation(project(":feature:participe"))
    implementation(project(":feature:ajustes"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(project(":core:testing"))
    // Robolectric só tem runner JUnit 4: roda pelo motor Vintage ao lado dos testes JUnit 5.
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.junit4)
    testRuntimeOnly(libs.junit.vintage.engine)
    testImplementation(libs.androidx.compose.ui.test.manifest)
}
