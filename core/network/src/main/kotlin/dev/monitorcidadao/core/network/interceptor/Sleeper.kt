package dev.monitorcidadao.core.network.interceptor

import okhttp3.Call
import java.io.IOException
import kotlin.time.Duration

/**
 * Espera dentro da cadeia do OkHttp (thread do dispatcher). Interrompe cedo se a chamada for
 * cancelada, para o cancelamento da coroutine chegar à espera de backoff/latência.
 */
fun interface Sleeper {
    @Throws(IOException::class)
    fun sleep(duration: Duration, call: Call)

    companion object {
        val Real: Sleeper = Sleeper { duration, call ->
            var remaining = duration.inWholeMilliseconds
            while (remaining > 0) {
                if (call.isCanceled()) throw IOException("Canceled")
                val slice = minOf(remaining, 25L)
                Thread.sleep(slice)
                remaining -= slice
            }
            if (call.isCanceled()) throw IOException("Canceled")
        }
    }
}
