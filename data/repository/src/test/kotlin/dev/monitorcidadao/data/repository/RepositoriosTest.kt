package dev.monitorcidadao.data.repository

import app.cash.turbine.test
import android.database.sqlite.SQLiteFullException
import dev.monitorcidadao.core.database.MonitorDatabase
import dev.monitorcidadao.core.database.dao.PessoaDao
import dev.monitorcidadao.core.database.entity.PessoaEntity
import dev.monitorcidadao.domain.AppError
import dev.monitorcidadao.domain.AppException
import dev.monitorcidadao.domain.PessoaId
import dev.monitorcidadao.domain.PostId
import dev.monitorcidadao.domain.PostTipo
import dev.monitorcidadao.domain.StatusPagina
import dev.monitorcidadao.domain.repository.EnvioSeguir
import dev.monitorcidadao.domain.usecase.ObterPerfil
import dev.monitorcidadao.domain.usecase.ResultadoSeguir
import dev.monitorcidadao.domain.usecase.SeguirPessoa
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
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
import java.util.concurrent.CountDownLatch

/** Repositórios concretos com Room real (em memória) + MockWebServer servindo as fixtures do contrato. */
@RunWith(RobolectricTestRunner::class)
class RepositoriosTest {
    private val ana = PessoaId("cam-1001")
    private val server = MockWebServer()
    private val rotas = FixtureDispatcher()
    private lateinit var db: MonitorDatabase
    private lateinit var pessoas: DefaultPessoaRepository
    private lateinit var posts: DefaultPostsRepository
    private lateinit var seguindo: DefaultSeguindoRepository

    @Before
    fun setUp() {
        server.dispatcher = rotas
        server.start()
        val http = server.httpClient()
        db = MonitorDatabase.inMemory(RuntimeEnvironment.getApplication())
        pessoas = DefaultPessoaRepository(http, db.pessoaDao(), relogio = { 1L })
        posts = DefaultPostsRepository(http, db.feedDao(), relogio = { 1L })
        seguindo = DefaultSeguindoRepository(http, db.seguidoDao(), db.filaEnvioDao(), relogio = { 1L })
    }

    @After
    fun tearDown() {
        db.close()
        server.close()
    }

    // --- Pessoa ---

    @Test
    fun `pessoa revalidada vai para o banco e o fluxo observado recebe`() = runTest {
        pessoas.observar(ana).test {
            assertNull(awaitItem())
            pessoas.revalidar(ana)
            assertEquals("Ana Ribeiro", awaitItem()!!.nome)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ObterPerfil serve o cache e revalida, e offline mantem o cache`() = runTest {
        pessoas.revalidar(ana) // aquece o cache
        rotas.falharCom = 503

        ObterPerfil(pessoas)(ana).test {
            var item = awaitItem()
            assertEquals("Ana Ribeiro", item.pessoa?.nome) // cache na primeira emissão
            while (item.revalidando) item = awaitItem()
            assertEquals(AppError.ServidorIndisponivel, item.erro)
            assertTrue(item.mostrandoDadosSalvos)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `pessoa inexistente vira NaoEncontrado`() = runTest {
        val erro = runCatching { pessoas.revalidar(PessoaId("nao-existe")) }.exceptionOrNull()
        assertEquals(AppError.NaoEncontrado, (erro as AppException).erro)
    }

    @Test
    fun `falha do banco ao gravar o perfil chega como AppException Desconhecido`() = runTest {
        val discoCheio = object : PessoaDao by db.pessoaDao() {
            override suspend fun salvar(pessoa: PessoaEntity) = throw SQLiteFullException("database or disk is full")
        }
        val repo = DefaultPessoaRepository(server.httpClient(), discoCheio)

        val erro = runCatching { repo.revalidar(ana) }.exceptionOrNull()

        assertEquals(AppError.Desconhecido, (erro as AppException).erro)
    }

    @Test
    fun `id que nao cabe na URL vira NaoEncontrado sem ir a rede`() = runTest {
        val erro = runCatching { pessoas.revalidar(PessoaId("..")) }.exceptionOrNull()

        assertEquals(AppError.NaoEncontrado, (erro as AppException).erro)
        assertTrue(rotas.pedidos.isEmpty())
    }

    // --- Posts ---

    @Test
    fun `post ja carregado na lista sai do cache por id e o desconhecido e null`() = runTest {
        assertNull(posts.post(PostId("post-cam-1001-01")))
        posts.postsDaPessoa(ana).loadFirst()

        assertEquals("Quer UBS abertas até as 22h nas cidades maiores", posts.post(PostId("post-cam-1001-01"))?.titulo)
        assertNull(posts.post(PostId("nao-existe")))
    }

    @Test
    fun `posts da pessoa paginam ate o fim pela rede e ficam no cache`() = runTest {
        val p = posts.postsDaPessoa(ana)

        p.loadFirst()
        while (!p.state.value.fim) p.loadNextIfNeeded(p.state.value.itens.last())

        val itens = p.state.value.itens
        assertEquals(12, itens.size)
        assertEquals(PostTipo.Desconhecido("enquete_futura"), itens.single { it.id == PostId("post-cam-1001-08") }.tipo)
        assertEquals(
            listOf("/v1/pessoas/cam-1001/posts", "/v1/pessoas/cam-1001/posts?cursor=p2", "/v1/pessoas/cam-1001/posts?cursor=p3"),
            rotas.pedidos.map { it.removePrefix("GET ") },
        )
        val salvo = db.feedDao().ler("pessoa:cam-1001")!!
        assertEquals(itens.map { it.id.valor }, salvo.posts.map { it.id })
        assertNull(salvo.proximoCursor)
    }

    @Test
    fun `segunda abertura mostra o cache na hora e revalida a primeira pagina`() = runTest {
        posts.postsDaPessoa(ana).run {
            loadFirst()
            loadNextIfNeeded(state.value.itens.last())
        }
        val segurar = CountDownLatch(1)
        rotas.segurar = segurar

        val p = posts.postsDaPessoa(ana)
        val carga = async { p.loadFirst() }
        p.state.test {
            var s = awaitItem()
            while (s.itens.isEmpty()) s = awaitItem()
            assertTrue("primeiro o cache", s.doCache)
            assertEquals(8, s.itens.size)
            segurar.countDown()
            while (s.doCache) s = awaitItem()
            assertEquals(4, s.itens.size) // primeira página nova substitui o feed salvo
            assertEquals(StatusPagina.Ocioso, s.status)
            cancelAndIgnoreRemainingEvents()
        }
        carga.await()
        assertEquals(1, db.feedDao().ler("pessoa:cam-1001")!!.paginas)
    }

    @Test
    fun `sem rede e com cache continua paginando do cursor salvo quando a rede volta`() = runTest {
        posts.postsDaPessoa(ana).loadFirst()
        rotas.falharCom = 503

        val p = posts.postsDaPessoa(ana)
        p.loadFirst()
        assertEquals(4, p.state.value.itens.size)
        assertEquals(AppError.ServidorIndisponivel, p.state.value.erroRevalidacao)

        rotas.falharCom = null
        p.loadNextIfNeeded(p.state.value.itens.last())
        assertEquals(8, p.state.value.itens.size)
        assertEquals(2, db.feedDao().ler("pessoa:cam-1001")!!.paginas)
    }

    // --- Seguindo ---

    @Test
    fun `SeguirPessoa otimista com 503 reverte no banco`() = runTest {
        val seguir = SeguirPessoa(seguindo)
        val resumo = dev.monitorcidadao.core.testing.Amostras.resumo()
        rotas.falharCom = 503
        val segurar = CountDownLatch(1)
        rotas.segurar = segurar

        seguindo.observarSeguindo(ana).test {
            assertFalse(awaitItem())
            val pedido = async { seguir(resumo, seguir = true) }
            assertTrue("otimista antes da resposta", awaitItem())
            assertEquals(listOf(EnvioSeguir(ana, true)), seguindo.pendentes())

            segurar.countDown()
            assertFalse("revertido depois do 503", awaitItem())
            assertEquals(ResultadoSeguir.Revertido(ana, false, AppError.ServidorIndisponivel), pedido.await())
        }
        assertTrue(seguindo.pendentes().isEmpty())
        assertEquals(listOf("POST /v1/seguindo/cam-1001"), rotas.pedidos)
    }

    @Test
    fun `seguir e deixar de seguir com sucesso confirmam e esvaziam a fila`() = runTest {
        val seguir = SeguirPessoa(seguindo)
        val resumo = dev.monitorcidadao.core.testing.Amostras.resumo()

        assertEquals(ResultadoSeguir.Confirmado(ana, true), seguir(resumo, seguir = true))
        seguindo.observarSeguidos().test { assertEquals(listOf("Ana Ribeiro"), awaitItem().map { it.nome }); cancelAndIgnoreRemainingEvents() }

        assertEquals(ResultadoSeguir.Confirmado(ana, false), seguir(resumo, seguir = false))
        seguindo.observarSeguidos().test { assertTrue(awaitItem().isEmpty()); cancelAndIgnoreRemainingEvents() }
        assertTrue(seguindo.pendentes().isEmpty())
        assertEquals(listOf("POST /v1/seguindo/cam-1001", "DELETE /v1/seguindo/cam-1001"), rotas.pedidos)
    }

    @Test
    fun `importa a lista do servidor uma vez so`() = runTest {
        seguindo.importarSeNecessario()
        seguindo.observarSeguidos().test {
            assertEquals(listOf("Ana Ribeiro", "Carlos Menezes", "Lúcia Farias"), awaitItem().map { it.nome })
            cancelAndIgnoreRemainingEvents()
        }

        seguindo.definirSeguindo(ana, false)
        seguindo.importarSeNecessario()

        seguindo.observarSeguindo(ana).test { assertFalse(awaitItem()); cancelAndIgnoreRemainingEvents() }
        assertEquals(1, rotas.pedidos.count { it == "GET /v1/seguindo" })
    }

    @Test
    fun `409 no POST e 404 no DELETE contam como sucesso (idempotentes)`() = runTest {
        val seguir = SeguirPessoa(seguindo)
        val resumo = dev.monitorcidadao.core.testing.Amostras.resumo()

        rotas.falharCom = 409
        assertEquals(ResultadoSeguir.Confirmado(ana, true), seguir(resumo, seguir = true))
        rotas.falharCom = 404
        assertEquals(ResultadoSeguir.Confirmado(ana, false), seguir(resumo, seguir = false))
        assertTrue(seguindo.pendentes().isEmpty())

        rotas.falharCom = 404 // 404 no POST não é "já estava assim": reverte
        assertEquals(ResultadoSeguir.Revertido(ana, false, AppError.NaoEncontrado), seguir(resumo, seguir = true))
    }

    @Test
    fun `importacao que falhou offline nao some depois que o usuario segue alguem localmente`() = runTest {
        val outra = dev.monitorcidadao.core.testing.Amostras.resumo(id = PessoaId("cam-9999"), nome = "Zélia Prado")
        rotas.falharCom = 503
        assertEquals(AppError.ServidorIndisponivel, (runCatching { seguindo.importarSeNecessario() }.exceptionOrNull() as AppException).erro)

        seguindo.marcar(outra, seguir = true) // seguiu alguém antes da importação dar certo
        seguindo.marcar(dev.monitorcidadao.core.testing.Amostras.resumo(), seguir = false) // e deixou de seguir a Ana
        rotas.falharCom = null
        seguindo.importarSeNecessario()

        seguindo.observarSeguidos().test {
            assertEquals(listOf("Carlos Menezes", "Lúcia Farias", "Zélia Prado"), awaitItem().map { it.nome })
            cancelAndIgnoreRemainingEvents()
        }
        seguindo.importarSeNecessario() // já importou: não busca de novo
        assertEquals(2, rotas.pedidos.count { it == "GET /v1/seguindo" }) // a que falhou + a que deu certo
    }
}
