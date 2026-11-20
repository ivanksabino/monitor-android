import dev.monitorcidadao.buildlogic.DependencyRules
import dev.monitorcidadao.buildlogic.JAVA_VERSION
import dev.monitorcidadao.buildlogic.configureKotlinCompilerOptions
import dev.monitorcidadao.buildlogic.configureUnitTests
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure

/** Módulo Kotlin/JVM puro (sem Android), como o `:domain`. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JAVA_VERSION
            targetCompatibility = JAVA_VERSION
        }
        configureKotlinCompilerOptions()
        configureUnitTests()
        DependencyRules.enforce(this)
    }
}
