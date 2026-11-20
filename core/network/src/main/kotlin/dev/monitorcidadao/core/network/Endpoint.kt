package dev.monitorcidadao.core.network

import kotlinx.serialization.KSerializer

/** Prioridade de uma chamada no [RequestScheduler]. A ordem da enum é a ordem de atendimento. */
enum class Priority {
    /** Tela visível. */
    Alta,

    /** Próxima página, imagens. */
    Utilidade,

    /** Sincronização em segundo plano. */
    Fundo,
}

enum class HttpMethod(val isIdempotent: Boolean) {
    GET(true),
    HEAD(true),
    PUT(true),
    DELETE(true),
    OPTIONS(true),
    POST(false),
    PATCH(false),
}

enum class CachePolicy {
    /** Segue os cabeçalhos HTTP do servidor. */
    Default,

    /** Força revalidação na rede. */
    NoCache,

    /** Não grava nem lê cache HTTP. */
    NoStore,
}

class EndpointBody(val content: String, val contentType: String = "application/json; charset=utf-8")

/**
 * Descrição declarativa de uma chamada: o [HTTPClient] monta, agenda, executa e decodifica.
 * Para respostas sem corpo (204), use `Unit.serializer()`.
 */
interface Endpoint<T> {
    /** Já codificado (percent-encoding): quem monta com valores variáveis codifica cada segmento. */
    val path: String
    val method: HttpMethod get() = HttpMethod.GET
    val query: Map<String, String?> get() = emptyMap()
    val headers: Map<String, String> get() = emptyMap()
    val body: EndpointBody? get() = null
    val responseSerializer: KSerializer<T>
    val priority: Priority get() = Priority.Alta
    val cachePolicy: CachePolicy get() = CachePolicy.Default
}

/** Implementação pronta para quem não precisa de uma classe própria por endpoint. */
data class HttpEndpoint<T>(
    override val path: String,
    override val responseSerializer: KSerializer<T>,
    override val method: HttpMethod = HttpMethod.GET,
    override val query: Map<String, String?> = emptyMap(),
    override val headers: Map<String, String> = emptyMap(),
    override val body: EndpointBody? = null,
    override val priority: Priority = Priority.Alta,
    override val cachePolicy: CachePolicy = CachePolicy.Default,
) : Endpoint<T>
