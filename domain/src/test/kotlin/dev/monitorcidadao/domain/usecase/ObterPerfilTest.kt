package dev.monitorcidadao.domain.usecase

import app.cash.turbine.test
import dev.monitorcidadao.core.testing.Amostras
import dev.monitorcidadao.core.testing.FakePessoaRepository
import dev.monitorcidadao.domain.AppError
import dev.monitorcidadao.domain.AppException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ObterPerfilTest {
    private val id = Amostras.anaId

    @Test
    fun `mostra o cache na hora e depois o valor revalidado`() = runTest {
        val antigo = Amostras.pessoa(presencaPct = 80)
        val novo = Amostras.pessoa(presencaPct = 92)
        val rede = CompletableDeferred<Unit>()
        val repo = FakePessoaRepository(mapOf(id to antigo)).apply {
            revalidarCom = { rede.await(); novo }
        }

        ObterPerfil(repo)(id).test {
            val cache = awaitItem()
            assertEquals(antigo, cache.pessoa)
            assertTrue(cache.revalidando)
            assertFalse(cache.mostrandoDadosSalvos)

            rede.complete(Unit)
            var item = awaitItem()
            while (item.revalidando || item.pessoa != novo) item = awaitItem()
            assertEquals(novo, item.pessoa)
            assertNull(item.erro)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sem cache e sem rede vira erro sem pessoa`() = runTest {
        val repo = FakePessoaRepository().apply { revalidarCom = { throw AppException(AppError.SemConexao) } }

        ObterPerfil(repo)(id).test {
            var item = awaitItem()
            while (item.revalidando) item = awaitItem()
            assertNull(item.pessoa)
            assertEquals(AppError.SemConexao, item.erro)
            assertFalse(item.mostrandoDadosSalvos)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `offline com cache mantem o cache e avisa dados salvos`() = runTest {
        val repo = FakePessoaRepository(mapOf(id to Amostras.pessoa())).apply {
            revalidarCom = { throw AppException(AppError.ServidorIndisponivel) }
        }

        ObterPerfil(repo)(id).test {
            var item = awaitItem()
            while (item.revalidando) item = awaitItem()
            assertEquals(Amostras.pessoa(), item.pessoa)
            assertEquals(AppError.ServidorIndisponivel, item.erro)
            assertTrue(item.mostrandoDadosSalvos)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `puxar para atualizar forca a rede`() = runTest {
        val repo = FakePessoaRepository().apply { revalidarCom = { Amostras.pessoa() } }

        ObterPerfil(repo)(id, forcarRede = true).test {
            var item = awaitItem()
            while (item.pessoa == null) item = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf(id to true), repo.revalidacoes)
    }

    @Test
    fun `falha inesperada do repositorio vira erro Desconhecido sem derrubar o fluxo`() = runTest {
        val repo = FakePessoaRepository(mapOf(id to Amostras.pessoa())).apply {
            revalidarCom = { throw IllegalStateException("banco travado") }
        }

        ObterPerfil(repo)(id).test {
            var item = awaitItem()
            while (item.revalidando) item = awaitItem()
            assertEquals(Amostras.pessoa(), item.pessoa)
            assertEquals(AppError.Desconhecido, item.erro)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
