package dev.monitorcidadao.data.repository.paging

import dev.monitorcidadao.domain.AppError
import dev.monitorcidadao.domain.AppException
import dev.monitorcidadao.domain.Pagina
import dev.monitorcidadao.domain.StatusPagina
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CursorPaginatorTest {

    /** Servidor falso: 3 páginas de 4 itens ("1".."12"), cursores p2/p3, com falhas programáveis. */
    private class FakeSource(
        val paginas: Map<String?, Pagina<String>> = mapOf(
            null to Pagina(listOf("1", "2", "3", "4"), "p2"),
            "p2" to Pagina(listOf("5", "6", "7", "8"), "p3"),
            "p3" to Pagina(listOf("9", "10", "11", "12"), null),
        ),
        var cache: Pagina<String>? = null,
    ) : PageSource<String> {
        val pedidos = mutableListOf<Pair<String?, Boolean>>()
        val falhas = mutableMapOf<String?, ArrayDeque<AppError>>()
        var segurar: CompletableDeferred<Unit>? = null

        fun falharUmaVez(cursor: String?, erro: AppError) {
            falhas.getOrPut(cursor) { ArrayDeque() }.addLast(erro)
        }

        override suspend fun cached() = cache

        override suspend fun fetch(cursor: String?, forcarRede: Boolean): Pagina<String> {
            pedidos += cursor to forcarRede
            segurar?.await()
            falhas[cursor]?.removeFirstOrNull()?.let { throw AppException(it) }
            return paginas.getValue(cursor)
        }
    }

    private fun paginator(source: FakeSource) = CursorPaginator(source, idOf = { it }, limiar = 2)

    @Test
    fun `pagina ate o fim com erro e retry no meio`() = runTest {
        val source = FakeSource().apply { falharUmaVez("p3", AppError.SemConexao) }
        val p = paginator(source)

        p.loadFirst()
        assertEquals(listOf("1", "2", "3", "4"), p.state.value.itens)
        assertEquals(StatusPagina.Ocioso, p.state.value.status)

        p.loadNextIfNeeded("1") // longe do fim: não pede
        assertEquals(listOf<String?>(null), source.pedidos.map { it.first })

        p.loadNextIfNeeded("3") // dentro do limiar
        assertEquals(8, p.state.value.itens.size)

        p.loadNextIfNeeded("8")
        assertEquals(StatusPagina.Erro(AppError.SemConexao, primeiraPagina = false), p.state.value.status)
        assertEquals(8, p.state.value.itens.size, "erro no rodapé mantém os itens")

        p.loadNextIfNeeded("8") // em erro não insiste sozinho: só pelo retry
        assertEquals(listOf(null, "p2", "p3"), source.pedidos.map { it.first })

        p.retry()
        assertEquals((1..12).map(Int::toString), p.state.value.itens)
        assertEquals(StatusPagina.Fim, p.state.value.status)
        assertTrue(p.state.value.fim)

        p.loadNextIfNeeded("12") // no fim não pede mais nada
        assertEquals(listOf(null, "p2", "p3", "p3"), source.pedidos.map { it.first })
    }

    @Test
    fun `erro na primeira pagina sem cache e retry`() = runTest {
        val source = FakeSource().apply { falharUmaVez(null, AppError.ServidorIndisponivel) }
        val p = paginator(source)

        p.loadFirst()
        assertEquals(StatusPagina.Erro(AppError.ServidorIndisponivel, primeiraPagina = true), p.state.value.status)
        assertTrue(p.state.value.itens.isEmpty())

        p.retry()
        assertEquals(listOf("1", "2", "3", "4"), p.state.value.itens)
    }

    @Test
    fun `trava de pagina duplicada ignora pedidos enquanto carrega`() = runTest {
        val source = FakeSource()
        val p = paginator(source)
        p.loadFirst()
        source.segurar = CompletableDeferred()

        val primeiro = launch { p.loadNextIfNeeded("4") }
        testScheduler.runCurrent()
        assertEquals(StatusPagina.CarregandoMais, p.state.value.status)
        p.loadNextIfNeeded("4")
        p.loadNextIfNeeded("3")

        source.segurar!!.complete(Unit)
        primeiro.join()
        assertEquals(listOf(null, "p2"), source.pedidos.map { it.first })
        assertEquals(8, p.state.value.itens.size)
    }

    @Test
    fun `dedup por id ao juntar paginas e cursor repetido vira fim`() = runTest {
        val source = FakeSource(
            paginas = mapOf(
                null to Pagina(listOf("1", "2"), "p2"),
                "p2" to Pagina(listOf("2", "3"), "p2"), // servidor em loop
            ),
        )
        val p = paginator(source)

        p.loadFirst()
        p.loadNextIfNeeded("2")

        assertEquals(listOf("1", "2", "3"), p.state.value.itens)
        assertEquals(StatusPagina.Fim, p.state.value.status)
    }

    @Test
    fun `mostra o cache na hora e troca pela rede`() = runTest {
        val source = FakeSource(cache = Pagina(listOf("antigo", "1"), "p2"))
        source.segurar = CompletableDeferred()
        val p = paginator(source)

        val carga = launch { p.loadFirst() }
        testScheduler.runCurrent()
        assertEquals(listOf("antigo", "1"), p.state.value.itens)
        assertTrue(p.state.value.doCache)

        source.segurar!!.complete(Unit)
        carga.join()
        assertEquals(listOf("1", "2", "3", "4"), p.state.value.itens)
        assertFalse(p.state.value.doCache)
    }

    @Test
    fun `offline com cache mantem os itens e marca o erro de revalidacao`() = runTest {
        val source = FakeSource(cache = Pagina(listOf("1", "2", "3", "4"), "p2")).apply {
            falharUmaVez(null, AppError.SemConexao)
        }
        val p = paginator(source)

        p.loadFirst()

        assertEquals(listOf("1", "2", "3", "4"), p.state.value.itens)
        assertTrue(p.state.value.doCache)
        assertEquals(AppError.SemConexao, p.state.value.erroRevalidacao)
        assertEquals(StatusPagina.Ocioso, p.state.value.status)

        p.loadNextIfNeeded("4") // continua do cursor salvo
        assertEquals(8, p.state.value.itens.size)
    }

    @Test
    fun `loadFirst repetido nao busca de novo e refresh forca a rede`() = runTest {
        val source = FakeSource()
        val p = paginator(source)

        p.loadFirst()
        p.loadNextIfNeeded("4")
        p.loadFirst()
        assertEquals(listOf<Pair<String?, Boolean>>(null to false, "p2" to false), source.pedidos)

        p.refresh()
        assertEquals(null to true, source.pedidos.last())
        assertEquals(listOf("1", "2", "3", "4"), p.state.value.itens)
        assertFalse(p.state.value.atualizando)
        assertNull(p.state.value.erroRevalidacao)
    }

    @Test
    fun `refresh que falha mantem a lista e avisa`() = runTest {
        val source = FakeSource()
        val p = paginator(source)
        p.loadFirst()
        source.falharUmaVez(null, AppError.SemConexao)

        p.refresh()

        assertEquals(listOf("1", "2", "3", "4"), p.state.value.itens)
        assertEquals(AppError.SemConexao, p.state.value.erroRevalidacao)
        assertFalse(p.state.value.atualizando)
    }

    @Test
    fun `cancelar a carga da proxima pagina libera para tentar de novo`() = runTest {
        val source = FakeSource()
        val p = paginator(source)
        p.loadFirst()
        source.segurar = CompletableDeferred()

        val carga = launch { p.loadNextIfNeeded("4") }
        testScheduler.runCurrent()
        carga.cancel()
        carga.join()
        assertEquals(StatusPagina.Ocioso, p.state.value.status)

        source.segurar = null
        p.loadNextIfNeeded("4")
        assertEquals(8, p.state.value.itens.size)
    }

    @Test
    fun `refresh durante a proxima pagina em voo espera e depois substitui pela primeira pagina nova`() = runTest {
        val source = FakeSource()
        val p = paginator(source)
        p.loadFirst()
        source.segurar = CompletableDeferred()

        val proxima = launch { p.loadNextIfNeeded("4") }
        testScheduler.runCurrent()
        val refresh = launch { p.refresh() }
        testScheduler.runCurrent()
        assertEquals(listOf<String?>(null, "p2"), source.pedidos.map { it.first }, "refresh espera a carga em voo")

        source.segurar!!.complete(Unit)
        proxima.join()
        refresh.join()
        assertEquals(listOf<Pair<String?, Boolean>>(null to false, "p2" to false, null to true), source.pedidos)
        assertEquals(listOf("1", "2", "3", "4"), p.state.value.itens)
        assertEquals(StatusPagina.Ocioso, p.state.value.status)

        p.loadNextIfNeeded("4") // cursores consumidos foram limpos: p2 pode ser pedido de novo
        assertEquals("p2" to false, source.pedidos.last())
        assertEquals(8, p.state.value.itens.size)
    }

    @Test
    fun `fim da lista do cache durante revalidacao que falha pede a proxima ao terminar, sem duplicar`() = runTest {
        val source = FakeSource(cache = Pagina(listOf("1", "2", "3", "4"), "p2")).apply {
            falharUmaVez(null, AppError.SemConexao)
        }
        source.segurar = CompletableDeferred()
        val p = paginator(source)

        val carga = launch { p.loadFirst() }
        testScheduler.runCurrent()
        assertTrue(p.state.value.doCache)
        p.loadNextIfNeeded("4") // chegou ao fim do cache com a revalidação em voo
        p.loadNextIfNeeded("4")
        assertEquals(listOf<String?>(null), source.pedidos.map { it.first }, "nenhum fetch duplicado durante a carga")

        source.segurar!!.complete(Unit)
        carga.join()
        assertEquals(listOf<String?>(null, "p2"), source.pedidos.map { it.first })
        assertEquals(8, p.state.value.itens.size)
        assertEquals(AppError.SemConexao, p.state.value.erroRevalidacao)
    }

    @Test
    fun `pedido adiado que deixou de estar perto do fim nao dispara nada`() = runTest {
        val source = FakeSource(
            paginas = mapOf(null to Pagina((1..10).map(Int::toString), "p2")),
            cache = Pagina(listOf("1", "2"), "p2"),
        )
        source.segurar = CompletableDeferred()
        val p = paginator(source)

        val carga = launch { p.loadFirst() }
        testScheduler.runCurrent()
        p.loadNextIfNeeded("2")
        source.segurar!!.complete(Unit)
        carga.join()

        assertEquals(listOf<String?>(null), source.pedidos.map { it.first })
        assertEquals(10, p.state.value.itens.size)
    }
}
