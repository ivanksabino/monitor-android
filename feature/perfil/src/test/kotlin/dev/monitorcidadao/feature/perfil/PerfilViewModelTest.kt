package dev.monitorcidadao.feature.perfil

import app.cash.turbine.test
import dev.monitorcidadao.core.routing.Route
import dev.monitorcidadao.core.routing.Router
import dev.monitorcidadao.core.routing.Sheet
import dev.monitorcidadao.core.testing.Amostras
import dev.monitorcidadao.core.testing.AmostrasPosts
import dev.monitorcidadao.core.testing.FakePaginator
import dev.monitorcidadao.core.testing.FakePessoaRepository
import dev.monitorcidadao.core.testing.FakeSeguindoRepository
import dev.monitorcidadao.core.testing.MainDispatcherExtension
import dev.monitorcidadao.domain.AppError
import dev.monitorcidadao.domain.AppException
import dev.monitorcidadao.domain.PessoaId
import dev.monitorcidadao.domain.Post
import dev.monitorcidadao.domain.StatusPagina
import dev.monitorcidadao.domain.repository.EnvioSeguir
import dev.monitorcidadao.domain.repository.SeguindoRepository
import dev.monitorcidadao.domain.usecase.ObterPerfil
import dev.monitorcidadao.domain.usecase.SeguirPessoa
import dev.monitorcidadao.feature.perfil.PerfilViewModel.Action
import dev.monitorcidadao.feature.perfil.PerfilViewModel.Efeito
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class PerfilViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val ana = Amostras.anaId
    private val pessoas = FakePessoaRepository().apply { revalidarCom = { Amostras.pessoa() } }
    private val seguindo = FakeSeguindoRepository()
    private val router = Router(Route.Inicio)
    private val paginator = FakePaginator(AmostrasPosts.paginas(quantas = 3, porPagina = 4))

    private fun viewModel(pessoaRepo: FakePessoaRepository = pessoas) = PerfilViewModel(
        id = ana,
        obterPerfil = ObterPerfil(pessoaRepo),
        seguir = SeguirPessoa(seguindo),
        seguindo = seguindo,
        router = router,
        paginator = paginator,
    )

    private fun PerfilViewModel.carregado() = apply { send(Action.Apareceu) }

    // --- Carregar ---

    @Test
    fun `carrega perfil e primeira pagina ao aparecer`() = runTest(main.dispatcher) {
        val vm = viewModel()
        assertTrue(vm.state.value.carregando)

        vm.send(Action.Apareceu)

        val s = vm.state.value
        assertEquals("Ana Ribeiro", s.pessoa?.nome)
        assertFalse(s.carregando)
        assertNull(s.erro)
        assertFalse(s.mostrandoDadosSalvos)
        assertEquals(4, s.posts.size)
        assertEquals(StatusPagina.Ocioso, s.paginacao)
        assertFalse(s.seguindo)
    }

    @Test
    fun `aparecer de novo nao recarrega`() = runTest(main.dispatcher) {
        val vm = viewModel().carregado()
        vm.send(Action.Apareceu)

        assertEquals(1, pessoas.revalidacoes.size)
        assertEquals(listOf(0), paginator.pedidas)
    }

    @Test
    fun `sem cache e sem rede mostra erro de tela cheia e tentar de novo recupera`() = runTest(main.dispatcher) {
        pessoas.revalidarCom = { throw AppException(AppError.ServidorIndisponivel) }
        val vm = viewModel().carregado()

        assertNull(vm.state.value.pessoa)
        assertEquals(AppError.ServidorIndisponivel, vm.state.value.erro)
        assertFalse(vm.state.value.carregando)

        pessoas.revalidarCom = { Amostras.pessoa() }
        vm.send(Action.TocouTentarDeNovo)

        assertEquals("Ana Ribeiro", vm.state.value.pessoa?.nome)
        assertNull(vm.state.value.erro)
    }

    @Test
    fun `com cache e offline mostra o cache e o aviso de dados salvos`() = runTest(main.dispatcher) {
        val comCache = FakePessoaRepository(mapOf(ana to Amostras.pessoa())).apply {
            revalidarCom = { throw AppException(AppError.SemConexao) }
        }
        val vm = viewModel(comCache).carregado()

        val s = vm.state.value
        assertEquals("Ana Ribeiro", s.pessoa?.nome)
        assertNull(s.erro) // erro de tela cheia só sem cache
        assertTrue(s.mostrandoDadosSalvos)
    }

    // --- Paginação ---

    @Test
    fun `pagina ate o fim chegando perto do ultimo post`() = runTest(main.dispatcher) {
        val vm = viewModel().carregado()

        var voltas = 0
        while (vm.state.value.paginacao != StatusPagina.Fim && voltas++ < 10) {
            vm.send(Action.ChegouPerto(vm.state.value.posts.last().id))
        }

        assertEquals(StatusPagina.Fim, vm.state.value.paginacao)
        assertEquals(12, vm.state.value.posts.size)
        assertEquals(12, vm.state.value.posts.map(Post::id).distinct().size)
        assertEquals(listOf(0, 1, 2), paginator.pedidas)
    }

    @Test
    fun `chegar perto de um post do meio nao pede pagina`() = runTest(main.dispatcher) {
        val vm = viewModel().carregado()

        vm.send(Action.ChegouPerto(vm.state.value.posts.first().id))

        assertEquals(listOf(0), paginator.pedidas)
    }

    @Test
    fun `erro de pagina vai para o rodape e tentar de novo continua do mesmo ponto`() = runTest(main.dispatcher) {
        paginator.falhas += listOf(null, AppError.SemConexao)
        val vm = viewModel().carregado()

        vm.send(Action.ChegouPerto(vm.state.value.posts.last().id))

        assertEquals(StatusPagina.Erro(AppError.SemConexao, primeiraPagina = false), vm.state.value.paginacao)
        assertEquals(4, vm.state.value.posts.size)
        assertEquals("Ana Ribeiro", vm.state.value.pessoa?.nome) // o resto da tela continua

        vm.send(Action.TocouTentarDeNovoPagina)

        assertEquals(StatusPagina.Ocioso, vm.state.value.paginacao)
        assertEquals(8, vm.state.value.posts.size)
        assertEquals(listOf(0, 1, 1), paginator.pedidas)
    }

    // --- Seguir ---

    @Test
    fun `seguir e otimista e reverte com anuncio quando o servidor recusa`() = runTest(main.dispatcher) {
        val vm = viewModel().carregado()
        val segurar = CompletableDeferred<Unit>()
        seguindo.segurarEnvio = segurar
        seguindo.respostaDoServidor = AppError.ServidorIndisponivel

        vm.efeitos.test {
            vm.send(Action.TocouSeguir)
            assertTrue(vm.state.value.seguindo, "estado otimista antes da resposta")
            expectNoEvents()

            segurar.complete(Unit)

            assertEquals(Efeito.FalhaAoSeguir("Ana Ribeiro", seguir = true), awaitItem())
            assertFalse(vm.state.value.seguindo, "revertido")
        }
    }

    @Test
    fun `seguir com sucesso mantem o estado e nao anuncia`() = runTest(main.dispatcher) {
        val vm = viewModel().carregado()

        vm.efeitos.test {
            vm.send(Action.TocouSeguir)
            assertTrue(vm.state.value.seguindo)
            expectNoEvents()

            vm.send(Action.TocouSeguir)
            assertFalse(vm.state.value.seguindo)
            expectNoEvents()
        }
        assertTrue(seguindo.filaAtual.isEmpty())
    }

    @Test
    fun `deixar de seguir que falha anuncia com o verbo certo`() = runTest(main.dispatcher) {
        val vm = viewModel().carregado()
        vm.send(Action.TocouSeguir) // passa a seguir (sucesso)
        seguindo.respostaDoServidor = AppError.ServidorIndisponivel

        vm.efeitos.test {
            vm.send(Action.TocouSeguir)
            assertEquals(Efeito.FalhaAoSeguir("Ana Ribeiro", seguir = false), awaitItem())
        }
        assertTrue(vm.state.value.seguindo)
    }

    @Test
    fun `toque duplo antes do banco emitir alterna seguir e deixar de seguir`() = runTest(main.dispatcher) {
        // Banco "lento", como o Room: a emissão chega depois dos dois toques.
        val bancoLento = object : SeguindoRepository by seguindo {
            override fun observarSeguindo(id: PessoaId): Flow<Boolean> = seguindo.observarSeguindo(id).onEach { delay(100) }
        }
        val segurar = CompletableDeferred<Unit>()
        seguindo.segurarEnvio = segurar
        val vm = PerfilViewModel(ana, ObterPerfil(pessoas), SeguirPessoa(seguindo), bancoLento, router, paginator).carregado()
        advanceUntilIdle()

        vm.send(Action.TocouSeguir)
        assertTrue(vm.state.value.seguindo)
        vm.send(Action.TocouSeguir)
        assertFalse(vm.state.value.seguindo, "o segundo toque desfaz o primeiro")

        segurar.complete(Unit)
        advanceUntilIdle()

        assertFalse(vm.state.value.seguindo)
        assertFalse(seguindo.observarSeguindo(ana).first())
        assertEquals(EnvioSeguir(ana, seguir = false), seguindo.enviados.last())
    }

    @Test
    fun `seguir antes do perfil carregar e ignorado`() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.send(Action.TocouSeguir)
        assertTrue(seguindo.enviados.isEmpty())
    }

    // --- Segmento e seções ---

    @Test
    fun `troca de segmento e idempotente`() = runTest(main.dispatcher) {
        val vm = viewModel().carregado()

        vm.state.test {
            assertEquals(Segmento.Resumo, awaitItem().segmento)

            vm.send(Action.TocouSegmento(Segmento.EmNumeros))
            assertEquals(Segmento.EmNumeros, awaitItem().segmento)

            vm.send(Action.TocouSegmento(Segmento.EmNumeros))
            expectNoEvents() // tocar no já selecionado não muda nada nem recarrega
        }
        assertEquals(1, pessoas.revalidacoes.size)
        assertEquals(listOf(0), paginator.pedidas)
    }

    @Test
    fun `secao troca e volta para posts`() = runTest(main.dispatcher) {
        val vm = viewModel().carregado()
        vm.send(Action.TocouSecao(Secao.Votos))
        assertEquals(Secao.Votos, vm.state.value.secao)
        vm.send(Action.TocouSecao(Secao.Posts))
        assertEquals(Secao.Posts, vm.state.value.secao)
        assertEquals(4, vm.state.value.posts.size)
    }

    // --- Navegação e atualizar ---

    @Test
    fun `post, voltar, alertas e compartilhar passam pelo router`() = runTest(main.dispatcher) {
        val vm = viewModel().carregado()
        router.push(Route.Perfil(ana))
        val post = vm.state.value.posts.first().id

        vm.send(Action.TocouPost(post))
        assertEquals(Route.Post(post), router.current)

        vm.send(Action.TocouVoltar)
        assertEquals(Route.Perfil(ana), router.current)

        vm.send(Action.TocouAlertas)
        assertEquals(Sheet.Alertas(ana), router.sheet)

        vm.send(Action.TocouCompartilhar)
        assertEquals(Sheet.CompartilharPerfil(ana), router.sheet)
    }

    @Test
    fun `puxar para atualizar forca a rede no perfil e na primeira pagina`() = runTest(main.dispatcher) {
        val vm = viewModel().carregado()
        vm.send(Action.ChegouPerto(vm.state.value.posts.last().id)) // 8 posts

        vm.send(Action.PuxouParaAtualizar)

        assertFalse(vm.state.value.atualizando)
        assertEquals(listOf(ana to false, ana to true), pessoas.revalidacoes)
        assertEquals(listOf(0, 1, 0), paginator.pedidas)
        assertEquals(4, vm.state.value.posts.size)
    }
}
