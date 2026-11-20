package dev.monitorcidadao.feature.feed

import dev.monitorcidadao.core.routing.Route
import dev.monitorcidadao.core.routing.Router
import dev.monitorcidadao.core.testing.Amostras
import dev.monitorcidadao.core.testing.FakeSeguindoRepository
import dev.monitorcidadao.core.testing.MainDispatcherExtension
import dev.monitorcidadao.domain.AppError
import dev.monitorcidadao.domain.AppException
import dev.monitorcidadao.domain.PessoaId
import dev.monitorcidadao.domain.PessoaResumo
import dev.monitorcidadao.domain.repository.SeguindoRepository
import dev.monitorcidadao.feature.feed.FeedViewModel.Action
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class FeedViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val router = Router(Route.Inicio)

    private fun repoQueImporta(vararg nomes: String) = object : FakeSeguindoRepositoryDelegate() {
        override suspend fun importarSeNecessario() {
            nomes.forEachIndexed { i, nome -> base.marcar(Amostras.resumo(PessoaId("p-$i"), nome), true) }
        }
    }

    private fun repoQueFalha(erro: AppError) = object : FakeSeguindoRepositoryDelegate() {
        override suspend fun importarSeNecessario() = throw AppException(erro)
    }

    @Test
    fun `lista quem a pessoa segue e tocar abre o perfil`() = runTest(main.dispatcher) {
        val vm = FeedViewModel(repoQueImporta("Ana Ribeiro", "Lúcia Farias"), router)

        vm.send(Action.Apareceu)

        val s = vm.state.value
        assertFalse(s.carregando)
        assertNull(s.erro)
        assertEquals(listOf("Ana Ribeiro", "Lúcia Farias"), s.pessoas.map { it.nome })

        vm.send(Action.TocouPessoa(s.pessoas.first().id))
        assertEquals(Route.Perfil(PessoaId("p-0")), router.current)
    }

    @Test
    fun `falha na importacao sem ninguem salvo mostra erro e tentar de novo recupera`() = runTest(main.dispatcher) {
        var falhar = true
        val repo = object : FakeSeguindoRepositoryDelegate() {
            override suspend fun importarSeNecessario() {
                if (falhar) throw AppException(AppError.SemConexao)
                base.marcar(Amostras.resumo(), true)
            }
        }
        val vm = FeedViewModel(repo, router)

        vm.send(Action.Apareceu)
        assertEquals(AppError.SemConexao, vm.state.value.erro)
        assertTrue(vm.state.value.pessoas.isEmpty())

        falhar = false
        vm.send(Action.TocouTentarDeNovo)
        assertNull(vm.state.value.erro)
        assertEquals(listOf("Ana Ribeiro"), vm.state.value.pessoas.map { it.nome })
    }

    @Test
    fun `falha na importacao com lista local nao mostra erro`() = runTest(main.dispatcher) {
        val repo = repoQueFalha(AppError.ServidorIndisponivel)
        repo.base.marcar(Amostras.resumo(), true)
        val vm = FeedViewModel(repo, router)

        vm.send(Action.Apareceu)

        assertNull(vm.state.value.erro)
        assertEquals(1, vm.state.value.pessoas.size)
    }

    /** BUG-010: importação já feita (retorna na hora) e o banco demora a entregar a 1ª leitura. */
    private fun repoComBancoAtrasado(banco: MutableSharedFlow<List<PessoaResumo>>) = object : FakeSeguindoRepositoryDelegate() {
        override fun observarSeguidos(): Flow<List<PessoaResumo>> = banco
        override suspend fun importarSeNecessario() = Unit
    }

    @Test
    fun `importado e banco ainda sem 1a leitura mostra carregando, nao vazio`() = runTest(main.dispatcher) {
        val banco = MutableSharedFlow<List<PessoaResumo>>()
        val vm = FeedViewModel(repoComBancoAtrasado(banco), router)

        vm.send(Action.Apareceu)

        assertTrue(vm.state.value.carregando, "sem leitura do banco não pode cair em vazio")
        assertTrue(vm.state.value.pessoas.isEmpty())
        assertNull(vm.state.value.erro)

        banco.emit(listOf(Amostras.resumo()))
        assertFalse(vm.state.value.carregando)
        assertEquals(listOf("Ana Ribeiro"), vm.state.value.pessoas.map { it.nome })
    }

    @Test
    fun `vazio so depois que o banco confirma a lista vazia`() = runTest(main.dispatcher) {
        val banco = MutableSharedFlow<List<PessoaResumo>>()
        val vm = FeedViewModel(repoComBancoAtrasado(banco), router)

        vm.send(Action.Apareceu)
        assertTrue(vm.state.value.carregando)

        banco.emit(emptyList())
        assertFalse(vm.state.value.carregando)
        assertTrue(vm.state.value.pessoas.isEmpty())
        assertNull(vm.state.value.erro)
    }

    @Test
    fun `banco le antes da importacao terminar segue carregando ate ela acabar`() = runTest(main.dispatcher) {
        val importacao = CompletableDeferred<Unit>()
        val repo = object : FakeSeguindoRepositoryDelegate() {
            override suspend fun importarSeNecessario() = importacao.await()
        }
        val vm = FeedViewModel(repo, router)

        vm.send(Action.Apareceu)
        assertTrue(vm.state.value.carregando, "banco vazio, mas a importação ainda pode trazer gente")

        importacao.complete(Unit)
        assertFalse(vm.state.value.carregando)
    }
}

/** [FakeSeguindoRepository] com a importação sobrescrevível. */
private abstract class FakeSeguindoRepositoryDelegate(
    val base: FakeSeguindoRepository = FakeSeguindoRepository(),
) : SeguindoRepository by base
