package dev.monitorcidadao.data.api

import dev.monitorcidadao.core.network.CachePolicy
import dev.monitorcidadao.core.network.HttpEndpoint
import dev.monitorcidadao.core.network.HttpMethod
import dev.monitorcidadao.core.network.Priority
import dev.monitorcidadao.data.api.dto.PaginaPostsDto
import dev.monitorcidadao.data.api.dto.PessoaDto
import dev.monitorcidadao.data.api.dto.SeguindoDto
import kotlinx.serialization.builtins.serializer

/**
 * Endpoints da API v1 (contracts/api.md). Uso: `http.send(Endpoints.postsDaPessoa(id, cursor = c))`.
 * Ids são strings opacas do contrato; o Data converte de/para os ids tipados do domínio.
 * [forcarRede] = puxar para atualizar: força a rede e não pega carona em GET já em voo.
 *
 * Sem [forcarRede] a política é [CachePolicy.Default] de propósito: o app não tem cache HTTP
 * (`okhttp3.Cache`), o cache é o SQLite, então a revalidação do stale-while-revalidate sempre vai
 * ao servidor; `Default` só permite pegar carona numa GET idêntica já em voo e respeitar o
 * `Cache-Control` de caches intermediários (TTL do servidor).
 *
 * Ids entram no path por [segmento], codificados: um id com `/`, `?` ou `#` não muda a rota.
 */
object Endpoints {
    fun seguindo(forcarRede: Boolean = false) = HttpEndpoint(
        path = "/v1/seguindo",
        responseSerializer = SeguindoDto.serializer(),
        cachePolicy = cache(forcarRede),
    )

    fun pessoa(id: String, forcarRede: Boolean = false) = HttpEndpoint(
        path = "/v1/pessoas/${segmento(id)}",
        responseSerializer = PessoaDto.serializer(),
        cachePolicy = cache(forcarRede),
    )

    /** Primeira página (`cursor == null`) com prioridade da tela; as seguintes como utilidade. */
    fun postsDaPessoa(id: String, cursor: String? = null, forcarRede: Boolean = false) = HttpEndpoint(
        path = "/v1/pessoas/${segmento(id)}/posts",
        responseSerializer = PaginaPostsDto.serializer(),
        query = mapOf("cursor" to cursor),
        priority = if (cursor == null) Priority.Alta else Priority.Utilidade,
        cachePolicy = cache(forcarRede),
    )

    /** `204` sem corpo. */
    fun seguir(id: String) = HttpEndpoint(
        path = "/v1/seguindo/${segmento(id)}",
        responseSerializer = Unit.serializer(),
        method = HttpMethod.POST,
        cachePolicy = CachePolicy.NoStore,
    )

    /** `204` sem corpo. */
    fun deixarDeSeguir(id: String) = HttpEndpoint(
        path = "/v1/seguindo/${segmento(id)}",
        responseSerializer = Unit.serializer(),
        method = HttpMethod.DELETE,
        cachePolicy = CachePolicy.NoStore,
    )

    private fun cache(forcarRede: Boolean) = if (forcarRede) CachePolicy.NoCache else CachePolicy.Default

    /**
     * Codifica [id] como um segmento de path (RFC 3986): só os não reservados (`A-Z a-z 0-9 - . _ ~`)
     * passam como estão; o resto vai em UTF-8 percent-encoded. `.`, `..` e vazio não têm como ser
     * um segmento (a URL os resolve como diretório) e são recusados.
     * @throws IdInvalidoException
     */
    internal fun segmento(id: String): String {
        if (id.isEmpty() || id == "." || id == "..") throw IdInvalidoException(id)
        return buildString {
            for (byte in id.encodeToByteArray()) {
                val c = byte.toInt() and 0xFF
                if (c.toChar().isNaoReservado()) append(c.toChar()) else append('%').append(HEX[c shr 4]).append(HEX[c and 0xF])
            }
        }
    }

    private fun Char.isNaoReservado() = this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9' || this in "-._~"

    private const val HEX = "0123456789ABCDEF"
}

/** Id que não pode ir num segmento de URL (vazio, `.` ou `..`): nenhum recurso do servidor tem esse id. */
class IdInvalidoException(val id: String) : IllegalArgumentException("Id inválido para URL: '$id'")
