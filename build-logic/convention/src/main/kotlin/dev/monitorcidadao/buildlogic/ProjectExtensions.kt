package dev.monitorcidadao.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.version(alias: String): String =
    findVersion(alias).orElseThrow { IllegalStateException("Versão '$alias' ausente no catálogo") }.requiredVersion

internal fun VersionCatalog.library(alias: String): Any =
    findLibrary(alias).orElseThrow { IllegalStateException("Biblioteca '$alias' ausente no catálogo") }.get()

/** `:core:network` vira `dev.monitorcidadao.core.network`. */
internal val Project.defaultNamespace: String
    get() = "dev.monitorcidadao" + path.replace(':', '.').replace('-', '_')

/** Pasta de contratos comuns (iOS/Android). Sobrescreva com `-Pmonitor.contractsDir=...`. */
val Project.contractsFixturesDir
    get() = rootProject.layout.projectDirectory.dir(
        (findProperty("monitor.contractsDir") as String?) ?: "../contracts",
    ).dir("fixtures")
