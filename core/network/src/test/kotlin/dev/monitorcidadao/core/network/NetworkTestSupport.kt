package dev.monitorcidadao.core.network

import dev.monitorcidadao.core.network.interceptor.Sleeper
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Call
import okhttp3.EventListener
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

@Serializable
data class Eco(val valor: String)

fun ecoEndpoint(path: String = "/v1/eco", cursor: String? = null, method: HttpMethod = HttpMethod.GET) =
    HttpEndpoint(path, Eco.serializer(), method = method, query = mapOf("cursor" to cursor))

fun semCorpo(path: String, method: HttpMethod) = HttpEndpoint(path, Unit.serializer(), method = method)

fun ok(valor: String = "ok") = MockResponse.Builder().code(200).body("""{"valor":"$valor"}""").build()

/** Não dorme: só registra quanto esperaria (e respeita cancelamento). */
class RecordingSleeper : Sleeper {
    val sleeps = CopyOnWriteArrayList<Duration>()
    override fun sleep(duration: Duration, call: Call) {
        if (call.isCanceled()) throw IOException("Canceled")
        sleeps += duration
    }
}

/** Observa o ciclo de vida das `Call`s (para provar que o cancelamento chegou ao OkHttp). */
class CallEvents : EventListener.Factory {
    val canceled = CountDownLatch(1)
    val canceledCount = java.util.concurrent.atomic.AtomicInteger()
    override fun create(call: Call): EventListener = object : EventListener() {
        override fun canceled(call: Call) {
            canceledCount.incrementAndGet()
            canceled.countDown()
        }
    }
}

/** Segura as respostas até [release]; registra a ordem de chegada dos paths. */
class GateDispatcher(private val respond: (RecordedRequest) -> MockResponse = { ok(it.url.encodedPath) }) : Dispatcher() {
    private val latch = CountDownLatch(1)
    val arrived = CopyOnWriteArrayList<String>()
    val firstArrived = CountDownLatch(1)

    fun release() = latch.countDown()

    override fun dispatch(request: RecordedRequest): MockResponse {
        arrived += request.target
        firstArrived.countDown()
        latch.await(10, TimeUnit.SECONDS)
        return respond(request)
    }
}

fun MockWebServer.config(
    retrySleeper: RecordingSleeper = RecordingSleeper(),
    block: HTTPClientConfig.() -> HTTPClientConfig = { this },
): HTTPClientConfig = HTTPClientConfig(
    baseUrl = url("/").toString(),
    userAgent = "MonitorCidadao-Teste/1.0",
    retrySleeper = retrySleeper,
    random = kotlin.random.Random(42),
).block()

fun waitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean) {
    val end = System.currentTimeMillis() + timeoutMs
    while (!condition()) {
        check(System.currentTimeMillis() < end) { "condição não aconteceu em ${timeoutMs}ms" }
        Thread.sleep(5)
    }
}
