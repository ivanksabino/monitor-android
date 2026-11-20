package dev.monitorcidadao.core.network.fixture

import android.content.res.AssetManager
import java.io.FileNotFoundException

/** De onde vêm os JSONs de `contracts/fixtures` (assets no Debug, classpath no teste). */
fun interface FixtureSource {
    /** Conteúdo do arquivo [name] (ex.: `pessoa_cam-1001.json`) ou `null` se não existir. */
    fun read(name: String): String?
}

/** Lê de `assets/fixtures/`, que só existe no build Debug (copiado de contracts/ pelo Gradle). */
class AssetFixtureSource(
    private val assets: AssetManager,
    private val directory: String = "fixtures",
) : FixtureSource {
    override fun read(name: String): String? = try {
        assets.open("$directory/$name").bufferedReader().use { it.readText() }
    } catch (_: FileNotFoundException) {
        null
    }
}
