package dev.monitorcidadao.core.network.fixture

import dev.monitorcidadao.core.network.HttpEndpoint
import dev.monitorcidadao.core.network.HttpMethod
import dev.monitorcidadao.core.network.interceptor.Sleeper
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import okhttp3.Call
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration

@Serializable
data class Eco(val valor: String)

fun ecoEndpoint(path: String) = HttpEndpoint(path, Eco.serializer())

fun semCorpo(path: String, method: HttpMethod) = HttpEndpoint(path, Unit.serializer(), method = method)

/** Não dorme: só registra quanto esperaria (e respeita cancelamento). */
class RecordingSleeper : Sleeper {
    val sleeps = CopyOnWriteArrayList<Duration>()
    override fun sleep(duration: Duration, call: Call) {
        if (call.isCanceled()) throw IOException("Canceled")
        sleeps += duration
    }
}
