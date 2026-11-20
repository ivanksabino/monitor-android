@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.monitorcidadao.core.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PriorityGateTest {
    @Test
    fun `vaga liberada vai para a maior prioridade e depois por ordem de chegada`() = runTest {
        val gate = PriorityGate(permits = 1)
        val order = mutableListOf<String>()
        val hold = CompletableDeferred<Unit>()

        launch { gate.withPermit(Priority.Fundo) { order += "ocupando"; hold.await() } }
        runCurrent()
        listOf(
            "fundo-1" to Priority.Fundo,
            "utilidade" to Priority.Utilidade,
            "alta-1" to Priority.Alta,
            "fundo-2" to Priority.Fundo,
            "alta-2" to Priority.Alta,
        ).forEach { (name, priority) ->
            launch { gate.withPermit(priority) { order += name } }
            runCurrent()
        }
        assertEquals(5, gate.waitingCount)

        hold.complete(Unit)
        runCurrent()

        assertEquals(listOf("ocupando", "alta-1", "alta-2", "utilidade", "fundo-1", "fundo-2"), order)
        assertEquals(0, gate.activeCount)
    }

    @Test
    fun `teto de simultaneas e respeitado`() = runTest {
        val gate = PriorityGate(permits = 2)
        val hold = CompletableDeferred<Unit>()
        var running = 0
        var peak = 0
        repeat(5) {
            launch {
                gate.withPermit(Priority.Alta) {
                    running++
                    peak = maxOf(peak, running)
                    hold.await()
                    running--
                }
            }
        }
        runCurrent()
        assertEquals(2, gate.activeCount)
        assertEquals(3, gate.waitingCount)
        hold.complete(Unit)
        runCurrent()
        assertEquals(2, peak)
        assertEquals(0, gate.activeCount)
    }

    @Test
    fun `cancelar quem espera tira da fila sem vazar vaga`() = runTest {
        val gate = PriorityGate(permits = 1)
        val hold = CompletableDeferred<Unit>()
        val ran = mutableListOf<String>()
        launch { gate.withPermit(Priority.Alta) { hold.await() } }
        runCurrent()
        val waiting = launch { gate.withPermit(Priority.Alta) { ran += "cancelado" } }
        launch { gate.withPermit(Priority.Fundo) { ran += "fundo" } }
        runCurrent()

        waiting.cancel()
        runCurrent()
        assertEquals(1, gate.waitingCount)

        hold.complete(Unit)
        runCurrent()
        assertEquals(listOf("fundo"), ran)
        assertEquals(0, gate.activeCount)
    }
}
