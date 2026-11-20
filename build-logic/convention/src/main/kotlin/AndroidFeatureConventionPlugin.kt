import dev.monitorcidadao.buildlogic.libs
import dev.monitorcidadao.buildlogic.library
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

/** Feature: biblioteca Android com Compose que só enxerga Domain, DesignSystem e Routing. */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("monitor.android.library.compose")
        dependencies {
            add("implementation", project(":domain"))
            add("implementation", project(":core:designsystem"))
            add("implementation", project(":core:routing"))
            add("implementation", libs.library("androidx-lifecycle-runtime-compose"))
            add("implementation", libs.library("androidx-lifecycle-viewmodel-compose"))
            add("implementation", libs.library("kotlinx-coroutines-core"))
            add("testImplementation", project(":core:testing"))
        }
    }
}
