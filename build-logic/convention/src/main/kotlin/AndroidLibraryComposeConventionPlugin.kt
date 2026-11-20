import com.android.build.api.dsl.LibraryExtension
import dev.monitorcidadao.buildlogic.configureCompose
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Biblioteca Android com Compose ligado (DesignSystem, ImageLoader, Routing e base das features). */
class AndroidLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("monitor.android.library")
        extensions.configure<LibraryExtension> {
            configureCompose(this)
        }
    }
}
