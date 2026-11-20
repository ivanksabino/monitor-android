package dev.monitorcidadao.core.testing

/** Fixtures de `contracts/fixtures` copiadas pelo Gradle para o classpath (pasta `fixtures` do classpath). */
object Fixtures {
    fun read(name: String): String = readOrNull(name) ?: error("Fixture '$name' não está no classpath")

    fun readOrNull(name: String): String? =
        Fixtures::class.java.classLoader.getResourceAsStream("fixtures/$name")?.bufferedReader()?.use { it.readText() }
}
