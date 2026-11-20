@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.monitorcidadao.core.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class RequestSchedulerTest {
    private fun TestScope.scheduler(max: Int = 4) =
        RequestScheduler(max, scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)))

    @Test
    fun `mesma chave em voo executa uma vez so`() = runTest {
        val scheduler = scheduler()
        val calls = AtomicInteger()
        val hold = CompletableDeferred<String>()
        val a = async { scheduler.execute("h", Priority.Alta, "GET /x") { calls.incrementAndGet(); hold.await() } }
        val b = async { scheduler.execute("h", Priority.Alta, "GET /x") { calls.incrementAndGet(); hold.await() } }
        runCurrent()
        hold.complete("resposta")
        assertEquals("resposta", a.await())
        assertEquals("resposta", b.await())
        assertEquals(1, calls.get())
        assertEquals(0, scheduler.inFlightCount)
    }

    @Test
    fun `chaves diferentes nao se misturam`() = runTest {
        val scheduler = scheduler()
        val p2 = async { scheduler.execute("h", Priority.Alta, "GET /posts?cursor=p2") { "p2" } }
        val p3 = async { scheduler.execute("h", Priority.Alta, "GET /posts?cursor=p3") { "p3" } }
        assertEquals("p2", p2.await())
        assertEquals("p3", p3.await())
    }

    @Test
    fun `chamada compartilhada so cancela quando o ultimo desiste`() = runTest {
        val scheduler = scheduler()
        val cancelled = CompletableDeferred<Unit>()
        val block: suspend () -> String = {
            try {
                awaitCancellation()
            } finally {
                cancelled.complete(Unit)
            }
        }
        val a = async(start = CoroutineStart.UNDISPATCHED) { scheduler.execute("h", Priority.Alta, "k", block) }
        val b = async(start = CoroutineStart.UNDISPATCHED) { scheduler.execute("h", Priority.Alta, "k", block) }
        runCurrent()

        a.cancel()
        runCurrent()
        assertTrue(!cancelled.isCompleted, "um interessado ainda espera")

        b.cancel()
        runCurrent()
        assertTrue(cancelled.isCompleted, "último interessado desistiu: a chamada é cancelada")
        assertEquals(0, scheduler.inFlightCount)
    }

    @Test
    fun `erro da chamada compartilhada chega a todos e libera a chave`() = runTest {
        val scheduler = scheduler()
        val hold = CompletableDeferred<String>()
        val a = async { runCatching { scheduler.execute("h", Priority.Alta, "k") { hold.await() } } }
        val b = async { runCatching { scheduler.execute("h", Priority.Alta, "k") { hold.await() } } }
        runCurrent()
        hold.completeExceptionally(NetworkError.Timeout())
        assertTrue(a.await().exceptionOrNull() is NetworkError.Timeout)
        assertTrue(b.await().exceptionOrNull() is NetworkError.Timeout)
        assertEquals("nova", scheduler.execute("h", Priority.Alta, "k") { "nova" })
    }

    @Test
    fun `teto e por host`() = runTest {
        val scheduler = scheduler(max = 1)
        val hold = CompletableDeferred<Unit>()
        val started = mutableListOf<String>()
        async { scheduler.execute("a.invalid", Priority.Alta) { started += "a1"; hold.await() } }
        async { scheduler.execute("a.invalid", Priority.Alta) { started += "a2" } }
        async { scheduler.execute("b.invalid", Priority.Alta) { started += "b1" } }
        runCurrent()
        assertEquals(listOf("a1", "b1"), started)
        hold.complete(Unit)
        runCurrent()
        assertEquals(listOf("a1", "b1", "a2"), started)
    }
}
