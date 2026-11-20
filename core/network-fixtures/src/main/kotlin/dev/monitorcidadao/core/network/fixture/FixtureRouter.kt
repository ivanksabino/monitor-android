package dev.monitorcidadao.core.network.fixture

import okhttp3.HttpUrl

/** O que a fixture responde para uma rota do contrato (api.md). */
sealed interface FixtureResponse {
    data class Json(val file: String) : FixtureResponse
    data object NoContent : FixtureResponse
    data object NotFound : FixtureResponse
}

/** Mapeia método + caminho do api.md para o arquivo de fixture. Função pura. */
object FixtureRouter {
    private val pessoa = Regex("^/v1/pessoas/([^/]+)$")
    private val posts = Regex("^/v1/pessoas/([^/]+)/posts$")
    private val seguir = Regex("^/v1/seguindo/([^/]+)$")
    private val cursor = Regex("^p(\\d+)$")

    fun route(method: String, url: HttpUrl): FixtureResponse {
        val path = url.encodedPath.trimEnd('/').ifEmpty { "/" }
        return when {
            method == "GET" && path == "/v1/seguindo" -> FixtureResponse.Json("seguindo.json")
            method == "GET" && pessoa.matches(path) ->
                FixtureResponse.Json("pessoa_${pessoa.find(path)!!.groupValues[1]}.json")
            method == "GET" && posts.matches(path) -> {
                val id = posts.find(path)!!.groupValues[1]
                val page = url.queryParameter("cursor")?.let { cursor.find(it)?.groupValues?.get(1) } ?: "1"
                FixtureResponse.Json("posts_${id}_p$page.json")
            }
            (method == "POST" || method == "DELETE") && seguir.matches(path) -> FixtureResponse.NoContent
            else -> FixtureResponse.NotFound
        }
    }
}
