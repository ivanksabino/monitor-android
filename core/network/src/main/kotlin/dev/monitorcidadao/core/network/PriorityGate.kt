package dev.monitorcidadao.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import java.util.PriorityQueue

/**
 * Semáforo com fila por prioridade: quando todas as [permits] vagas estão ocupadas,
 * a próxima vaga liberada vai para o pedido de maior prioridade (e, empatado, o mais antigo).
 * Cancelar quem espera tira o pedido da fila sem vazar vaga.
 */
internal class PriorityGate(private val permits: Int) {
    init {
        require(permits > 0) { "permits deve ser > 0" }
    }

    private class Waiter(val priority: Priority, val order: Long) {
        val granted = CompletableDeferred<Unit>()
    }

    private val lock = Any()
    private val waiters = PriorityQueue(compareBy<Waiter>({ it.priority.ordinal }, { it.order }))
    private var active = 0
    private var nextOrder = 0L

    val activeCount: Int get() = synchronized(lock) { active }
    val waitingCount: Int get() = synchronized(lock) { waiters.size }

    suspend fun <R> withPermit(priority: Priority, block: suspend () -> R): R {
        acquire(priority)
        try {
            return block()
        } finally {
            release()
        }
    }

    private suspend fun acquire(priority: Priority) {
        val waiter = synchronized(lock) {
            if (active < permits && waiters.isEmpty()) {
                active++
                return
            }
            Waiter(priority, nextOrder++).also(waiters::add)
        }
        try {
            waiter.granted.await()
        } catch (e: CancellationException) {
            val stillQueued = synchronized(lock) { waiters.remove(waiter) }
            // Se já tinha recebido a vaga, devolve para o próximo da fila.
            if (!stillQueued) release()
            throw e
        }
    }

    private fun release() {
        val next = synchronized(lock) {
            waiters.poll().also { if (it == null) active-- }
        }
        next?.granted?.complete(Unit)
    }
}
