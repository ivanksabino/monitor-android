import com.android.build.api.dsl.ApplicationExtension
import dev.monitorcidadao.buildlogic.DependencyRules
import dev.monitorcidadao.buildlogic.configureCompose
import dev.monitorcidadao.buildlogic.configureKotlinAndroid
import dev.monitorcidadao.buildlogic.libs
import dev.monitorcidadao.buildlogic.version
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        extensions.configure<ApplicationExtension> {
            configureKotlinAndroid(this)
            configureCompose(this)
            defaultConfig.targetSdk = libs.version("targetSdk").toInt()
            testOptions.unitTests.all { it.useJUnitPlatform() }
        }
        DependencyRules.enforce(this)
    }
}
