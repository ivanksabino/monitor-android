package dev.monitorcidadao.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import java.util.concurrent.ConcurrentHashMap

/**
 * Fila de chamadas:
 * - **prioridade**: Alta (tela visível) > Utilidade (próxima página, imagens) > Fundo (sync);
 * - **teto por host**: no máximo [maxConcurrentPerHost] chamadas simultâneas para o mesmo host;
 * - **dedup**: chamadas com a mesma `dedupKey` em voo viram uma só (o cursor faz parte da chave);
 * - **cancelamento**: cancelar quem chamou cancela a chamada; na chamada compartilhada,
 *   só quando o último interessado desiste.
 */
class RequestScheduler(
    val maxConcurrentPerHost: Int = DEFAULT_MAX_PER_HOST,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val gates = ConcurrentHashMap<String, PriorityGate>()

    private class InFlight(val deferred: Deferred<Any?>) {
        var interested = 1
    }

    private val lock = Any()
    private val inFlight = HashMap<String, InFlight>()

    /** Quantidade de chamadas deduplicáveis em voo (para teste e diagnóstico). */
    val inFlightCount: Int get() = synchronized(lock) { inFlight.size }

    suspend fun <R> execute(
        host: String,
        priority: Priority,
        dedupKey: String? = null,
        block: suspend () -> R,
    ): R {
        if (dedupKey == null) return gate(host).withPermit(priority, block)

        val entry = synchronized(lock) {
            inFlight[dedupKey]?.also { it.interested++ }
                ?: InFlight(
                    scope.async(start = CoroutineStart.LAZY) { gate(host).withPermit(priority, block) },
                ).also { created ->
                    inFlight[dedupKey] = created
                    created.deferred.invokeOnCompletion {
                        synchronized(lock) { if (inFlight[dedupKey] === created) inFlight.remove(dedupKey) }
                    }
                }
        }
        entry.deferred.start()
        try {
            @Suppress("UNCHECKED_CAST")
            return entry.deferred.await() as R
        } catch (e: CancellationException) {
            leave(dedupKey, entry)
            throw e
        }
    }

    private fun leave(key: String, entry: InFlight) {
        val cancelShared = synchronized(lock) {
            entry.interested--
            (entry.interested == 0).also { last -> if (last && inFlight[key] === entry) inFlight.remove(key) }
        }
        if (cancelShared) entry.deferred.cancel()
    }

    private fun gate(host: String): PriorityGate =
        gates.getOrPut(host.lowercase()) { PriorityGate(maxConcurrentPerHost) }

    companion object {
        const val DEFAULT_MAX_PER_HOST = 4
    }
}
