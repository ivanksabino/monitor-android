package dev.monitorcidadao.core.database

import app.cash.turbine.test
import dev.monitorcidadao.core.database.entity.BlocoColuna
import dev.monitorcidadao.core.database.entity.MandatoColunas
import dev.monitorcidadao.core.database.entity.NumerosColunas
import dev.monitorcidadao.core.database.entity.PessoaEntity
import dev.monitorcidadao.core.database.entity.PostEntity
import dev.monitorcidadao.core.database.entity.ResumoColunas
import dev.monitorcidadao.core.database.entity.SeguidoEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Room real em memória (Robolectric): esquema, conversores, transações e observação reativa. */
@RunWith(RobolectricTestRunner::class)
class MonitorDatabaseTest {
    private lateinit var db: MonitorDatabase

    @Before
    fun setUp() {
        db = MonitorDatabase.inMemory(RuntimeEnvironment.getApplication())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `pessoa observada emite de novo a cada gravacao`() = runTest {
        db.pessoaDao().observar("cam-1001").test {
            assertNull(awaitItem())
            db.pessoaDao().salvar(pessoa(presenca = 80))
            assertEquals(80, awaitItem()!!.resumo.presencaPct)
            db.pessoaDao().salvar(pessoa(presenca = 92))
            val atual = awaitItem()!!
            assertEquals(92, atual.resumo.presencaPct)
            assertEquals(listOf(true, false, true), atual.numeros.presencaUltimas)
            assertEquals(listOf("TSE", "Câmara dos Deputados"), atual.numeros.fontes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `feed guarda paginas em ordem com cursor e blocos em JSON`() = runTest {
        val feed = db.feedDao()
        feed.salvarPagina("pessoa:a", cursor = null, proximoCursor = "p2", posts = listOf(post("1"), post("2")), agora = 1)
        feed.salvarPagina("pessoa:a", cursor = "p2", proximoCursor = null, posts = listOf(post("2"), post("3")), agora = 2)

        val salvo = feed.ler("pessoa:a")!!

        assertEquals(listOf("1", "2", "3"), salvo.posts.map { it.id })
        assertNull(salvo.proximoCursor)
        assertEquals(2, salvo.paginas)
        assertEquals(listOf(BlocoColuna.Placar(3, 1, 0), BlocoColuna.Fonte("TSE", null)), salvo.posts.first().blocos)
    }

    @Test
    fun `primeira pagina nova substitui o feed e nao mexe em outro feed`() = runTest {
        val feed = db.feedDao()
        feed.salvarPagina("pessoa:a", null, "p2", listOf(post("1")), 1)
        feed.salvarPagina("pessoa:a", "p2", "p3", listOf(post("2")), 1)
        feed.salvarPagina("pessoa:b", null, null, listOf(post("9")), 1)

        feed.salvarPagina("pessoa:a", null, "p2", listOf(post("0"), post("1")), 2)

        assertEquals(listOf("0", "1"), feed.ler("pessoa:a")!!.posts.map { it.id })
        assertEquals("p2", feed.ler("pessoa:a")!!.proximoCursor)
        assertEquals(listOf("9"), feed.ler("pessoa:b")!!.posts.map { it.id })
        assertNull(feed.ler("pessoa:c"))
    }

    @Test
    fun `seguidos filtram quem deixou de seguir e mantem o resumo`() = runTest {
        val dao = db.seguidoDao()
        dao.salvar(seguido("b", "Bruno", true))
        dao.salvar(seguido("a", "Ana", true))

        dao.observarSeguidos().test {
            assertEquals(listOf("Ana", "Bruno"), awaitItem().map { it.nome })
            dao.definirSeguindo("a", false, 2)
            assertEquals(listOf("Bruno"), awaitItem().map { it.nome })
            cancelAndIgnoreRemainingEvents()
        }
        dao.observarSeguindo("a").test { assertEquals(false, awaitItem()); cancelAndIgnoreRemainingEvents() }
        dao.observarSeguindo("z").test { assertNull(awaitItem()); cancelAndIgnoreRemainingEvents() }
        assertEquals(2, dao.contar())
    }

    @Test
    fun `fila de envio guarda um pedido por pessoa e o mais novo herda o estado no servidor`() = runTest {
        val fila = db.filaEnvioDao()
        fila.enfileirar("a", seguir = true, estadoNoServidor = false, agora = 1)
        fila.enfileirar("b", seguir = true, estadoNoServidor = false, agora = 2)
        fila.enfileirar("a", seguir = false, estadoNoServidor = true, agora = 3)

        assertEquals(listOf("b" to true, "a" to false), fila.pendentes().map { it.pessoaId to it.seguir })
        assertEquals("herda o estado do pedido substituído", false, fila.pendente("a")!!.estadoNoServidor)

        assertNull("pedido antigo: não apaga o novo", fila.descartar("a", seguir = true))
        fila.confirmar("a", seguir = true) // o antigo chegou: o servidor agora segue
        assertEquals(true, fila.pendente("a")!!.estadoNoServidor)

        assertEquals(true, fila.descartar("a", seguir = false))
        assertNull(fila.pendente("a"))
        fila.confirmar("b", seguir = true)
        assertTrue(fila.pendentes().isEmpty())
    }

    @Test
    fun `pagina fora da cadeia de cursores e descartada`() = runTest {
        val feed = db.feedDao()
        assertFalse("sem primeira página", feed.salvarPagina("pessoa:a", "p2", "p3", listOf(post("2")), 1))
        assertNull(feed.ler("pessoa:a"))

        feed.salvarPagina("pessoa:a", null, "p2", listOf(post("1")), 1)
        assertFalse("cursor que não continua a última", feed.salvarPagina("pessoa:a", "p9", "p10", listOf(post("9")), 1))
        assertTrue(feed.salvarPagina("pessoa:a", "p2", "p3", listOf(post("2")), 1))
        assertFalse("mesma página de novo", feed.salvarPagina("pessoa:a", "p2", "p3", listOf(post("2")), 1))

        val salvo = feed.ler("pessoa:a")!!
        assertEquals(listOf("1", "2"), salvo.posts.map { it.id })
        assertEquals("p3", salvo.proximoCursor)
        assertEquals(2, salvo.paginas)
    }

    @Test
    fun `importar seguidos mescla sem sobrescrever o local e marca como importado`() = runTest {
        val dao = db.seguidoDao()
        dao.salvar(seguido("a", "Ana", false)) // o usuário deixou de seguir antes da importação
        assertFalse(dao.importado())

        dao.importar(listOf(seguido("a", "Ana", true), seguido("b", "Bruno", true)))

        assertTrue(dao.importado())
        dao.observarSeguindo("a").test { assertEquals(false, awaitItem()); cancelAndIgnoreRemainingEvents() }
        dao.observarSeguindo("b").test { assertEquals(true, awaitItem()); cancelAndIgnoreRemainingEvents() }
    }

    @Test
    fun `blocos com JSON invalido no banco viram lista vazia`() {
        assertEquals(emptyList<BlocoColuna>(), Conversores().textoParaBlocos("{quebrado"))
        assertEquals(
            listOf(BlocoColuna.ValorDestaque(10, "gasto")),
            Conversores().run { textoParaBlocos(blocosParaTexto(listOf(BlocoColuna.ValorDestaque(10, "gasto")))) },
        )
    }

    private fun pessoa(presenca: Int) = PessoaEntity(
        id = "cam-1001", nome = "Ana Ribeiro", cargo = "deputada_federal", partido = "PEX", uf = "PB", iniciais = "AR",
        emExercicio = true,
        mandato = MandatoColunas(2027, 2031, 1),
        resumo = ResumoColunas(2027, presenca, 14, 11_234_018, 840_000_000),
        numeros = NumerosColunas(listOf(true, false, true), 1, 2, 3, 2026, 4, 94, listOf("TSE", "Câmara dos Deputados")),
        atualizadoEm = "2027-03-12T18:40:00-03:00",
        salvoEm = 0,
    )

    private fun post(id: String) = PostEntity(
        id = id, tipo = "votou", pessoaId = "a", publicadoEm = "2027-03-01T10:00:00-03:00", titulo = "T$id", corpo = "",
        blocos = listOf(BlocoColuna.Placar(3, 1, 0), BlocoColuna.Fonte("TSE", null)), temExplicacao = false,
    )

    private fun seguido(id: String, nome: String, seguindo: Boolean) =
        SeguidoEntity(id, nome, "deputada_federal", "PEX", "PB", nome.take(2), seguindo, 1)
}
