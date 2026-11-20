package dev.monitorcidadao.buildlogic

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency

/**
 * Regras de dependência da arquitetura (doc 06, seção 2), verificadas na configuração do build.
 *
 * ```
 * App ──▶ Feature* ──▶ Domain, DesignSystem, Routing
 * App ──▶ Data ──▶ Domain, APIClient, Database
 * APIClient ──▶ Network
 * NetworkFixtures (só Debug) ──▶ Network
 * DesignSystem ──▶ ImageLoader
 * Network, ImageLoader, Storage, Database ──▶ (nada do domínio)
 * ```
 * Configurações de teste ficam de fora (podem usar `:core:testing`).
 */
internal object DependencyRules {
    private val infra = setOf(
        ":core:network", ":core:imageloader", ":core:storage", ":core:database",
        ":core:analytics", ":core:featureflags",
    )

    /** `:app` pode depender de qualquer módulo; os demais só do que a arquitetura permite. */
    fun isAllowed(from: String, to: String): Boolean = when {
        from == ":app" -> true
        from.startsWith(":feature:") -> to in setOf(
            ":domain", ":core:designsystem", ":core:routing",
        )
        from == ":data:repository" -> to in setOf(":domain", ":data:api", ":core:database")
        from == ":data:api" -> to == ":core:network"
        from == ":core:network-fixtures" -> to == ":core:network"
        from == ":core:designsystem" -> to == ":core:imageloader"
        from == ":core:routing" -> to == ":domain"
        from == ":core:testing" -> to != ":app" && !to.startsWith(":feature:")
        from == ":domain" -> false
        from in infra -> false
        else -> false
    }

    private fun isTestConfiguration(name: String): Boolean =
        name.contains("test", ignoreCase = true) || name.contains("lint", ignoreCase = true)

    fun enforce(project: Project) {
        val from = project.path
        project.configurations.configureEach {
            val configurationName = name
            if (isTestConfiguration(configurationName)) return@configureEach
            dependencies.configureEach {
                if (this is ProjectDependency) {
                    val to = path
                    if (to != from && !isAllowed(from, to)) {
                        throw GradleException(
                            "Regra de arquitetura violada: $from não pode depender de $to " +
                                "(configuração '$configurationName'). Veja contracts/arquitetura.md §2.",
                        )
                    }
                }
            }
        }
    }
}
