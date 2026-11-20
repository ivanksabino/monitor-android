package dev.monitorcidadao.domain.usecase

import app.cash.turbine.test
import dev.monitorcidadao.core.testing.Amostras
import dev.monitorcidadao.core.testing.FakeSeguindoRepository
import dev.monitorcidadao.domain.AppError
import dev.monitorcidadao.domain.PessoaId
import dev.monitorcidadao.domain.repository.EnvioSeguir
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SeguirPessoaTest {
    private val ana = Amostras.resumo()
    private val bruno = Amostras.resumo(id = PessoaId("cam-1002"), nome = "Bruno Lima")
    private val repo = FakeSeguindoRepository()
    private val seguir = SeguirPessoa(repo)

    private suspend fun seguindoAgora(id: PessoaId = ana.id) = repo.observarSeguindo(id).first()

    @Test
    fun `sucesso confirma e esvazia a fila`() = runTest {
        val r = seguir(ana, seguir = true)

        assertEquals(ResultadoSeguir.Confirmado(ana.id, true), r)
        assertTrue(repo.filaAtual.isEmpty())
        assertTrue(seguindoAgora())
    }

    @Test
    fun `503 mostra o otimista e depois reverte`() = runTest {
        repo.respostaDoServidor = AppError.ServidorIndisponivel
        repo.segurarEnvio = CompletableDeferred()

        repo.observarSeguindo(ana.id).test {
            assertFalse(awaitItem())
            val pedido = async { seguir(ana, seguir = true) }
            assertTrue(awaitItem(), "estado otimista antes da resposta")

            repo.segurarEnvio!!.complete(Unit)
            assertFalse(awaitItem(), "revertido depois do 503")
            assertEquals(ResultadoSeguir.Revertido(ana.id, false, AppError.ServidorIndisponivel), pedido.await())
        }
        assertTrue(repo.filaAtual.isEmpty())
    }

    @Test
    fun `429 reverte como os outros erros do servidor (contrato)`() = runTest {
        repo.respostaDoServidor = AppError.MuitasRequisicoes(tenteEmSegundos = 30)

        val r = seguir(ana, seguir = true)

        assertEquals(ResultadoSeguir.Revertido(ana.id, false, AppError.MuitasRequisicoes(30)), r)
        assertTrue(repo.filaAtual.isEmpty())
        assertFalse(seguindoAgora())
    }

    @Test
    fun `sem conexao mantem otimista na fila e reenvia depois`() = runTest {
        repo.respostaDoServidor = AppError.SemConexao

        val r = seguir(ana, seguir = true)

        assertEquals(ResultadoSeguir.Pendente(ana.id, true, AppError.SemConexao), r)
        assertEquals(listOf(EnvioSeguir(ana.id, true)), repo.filaAtual)
        assertTrue(seguindoAgora())

        repo.respostaDoServidor = null
        assertEquals(listOf(ResultadoSeguir.Confirmado(ana.id, true)), seguir.reenviarPendentes())
        assertTrue(repo.filaAtual.isEmpty())
    }

    @Test
    fun `dois toques seguidos com servidor lento - o segundo otimista aparece antes da resposta do primeiro`() = runTest {
        repo.segurarEnvio = CompletableDeferred()

        repo.observarSeguindo(ana.id).test {
            assertFalse(awaitItem())
            val primeiro = async { seguir(ana, seguir = true) }
            assertTrue(awaitItem())
            val segundo = async { seguir(ana, seguir = false) }
            assertFalse(awaitItem(), "segundo toque aplicado com o primeiro ainda em voo")
            assertTrue(repo.enviados.isEmpty(), "nenhuma resposta ainda")

            repo.segurarEnvio!!.complete(Unit)
            assertEquals(ResultadoSeguir.Confirmado(ana.id, false), primeiro.await())
            assertEquals(ResultadoSeguir.Confirmado(ana.id, false), segundo.await())
            expectNoEvents()
        }
        assertEquals(listOf(EnvioSeguir(ana.id, true), EnvioSeguir(ana.id, false)), repo.enviados)
        assertTrue(repo.filaAtual.isEmpty())
    }

    @Test
    fun `toque em outra pessoa nao espera o envio lento da primeira`() = runTest {
        repo.segurarEnvio = CompletableDeferred()

        val ana1 = async { seguir(ana, seguir = true) }
        testScheduler.runCurrent()
        val bruno1 = async { seguir(bruno, seguir = true) }
        testScheduler.runCurrent()

        assertTrue(seguindoAgora(bruno.id), "otimista do Bruno com o envio da Ana em voo")
        repo.segurarEnvio!!.complete(Unit)
        ana1.await()
        bruno1.await()
        assertTrue(repo.filaAtual.isEmpty())
    }

    @Test
    fun `primeiro chega e o segundo falha - volta ao que o servidor tem`() = runTest {
        repo.segurarEnvio = CompletableDeferred()
        repo.respostas += listOf(null, AppError.ServidorIndisponivel)

        val primeiro = async { seguir(ana, seguir = true) }
        testScheduler.runCurrent()
        val segundo = async { seguir(ana, seguir = false) }
        testScheduler.runCurrent()
        assertFalse(seguindoAgora())

        repo.segurarEnvio!!.complete(Unit)
        val esperado = ResultadoSeguir.Revertido(ana.id, true, AppError.ServidorIndisponivel)
        assertEquals(esperado, primeiro.await())
        assertEquals(ResultadoSeguir.Confirmado(ana.id, true), segundo.await())
        assertTrue(seguindoAgora(), "o POST chegou: o servidor segue")
        assertTrue(repo.filaAtual.isEmpty())
    }

    @Test
    fun `seguir quem ja era seguido com 503 nao inverte o estado`() = runTest {
        repo.marcar(ana, seguir = true)
        repo.respostaDoServidor = AppError.ServidorIndisponivel

        val r = seguir(ana, seguir = true) // tela desatualizada

        assertEquals(ResultadoSeguir.Confirmado(ana.id, true), r)
        assertTrue(seguindoAgora())
        assertTrue(repo.filaAtual.isEmpty())
    }

    @Test
    fun `seguir offline e deixar de seguir online com 503 fica como o servidor - nao seguindo`() = runTest {
        repo.respostaDoServidor = AppError.SemConexao
        seguir(ana, seguir = true)

        repo.respostaDoServidor = AppError.ServidorIndisponivel
        val r = seguir(ana, seguir = false)

        assertEquals(ResultadoSeguir.Confirmado(ana.id, false), r)
        assertFalse(seguindoAgora(), "o servidor nunca recebeu o POST")
        assertTrue(repo.filaAtual.isEmpty())
    }

    @Test
    fun `seguir e deixar de seguir offline - na volta so o DELETE vai`() = runTest {
        repo.respostaDoServidor = AppError.SemConexao
        seguir(ana, seguir = true)
        seguir(ana, seguir = false)
        assertEquals(listOf(EnvioSeguir(ana.id, false)), repo.filaAtual)
        assertEquals(false, repo.estadoNoServidorDaFila(ana.id))

        repo.respostaDoServidor = null
        repo.enviados.clear()
        assertEquals(listOf(ResultadoSeguir.Confirmado(ana.id, false)), seguir.reenviarPendentes())

        assertEquals(listOf(EnvioSeguir(ana.id, false)), repo.enviados)
        assertFalse(seguindoAgora())
        assertTrue(repo.filaAtual.isEmpty())
    }

    @Test
    fun `toque durante o reenvio e enviado depois, na ordem`() = runTest {
        repo.respostaDoServidor = AppError.SemConexao
        seguir(ana, seguir = true)
        repo.respostaDoServidor = null
        repo.enviados.clear()
        repo.segurarEnvio = CompletableDeferred()

        val reenvio = async { seguir.reenviarPendentes() }
        testScheduler.runCurrent()
        val toque = async { seguir(ana, seguir = false) }
        testScheduler.runCurrent()
        assertFalse(seguindoAgora(), "otimista sem esperar o reenvio")

        repo.segurarEnvio!!.complete(Unit)
        assertEquals(listOf(ResultadoSeguir.Confirmado(ana.id, false)), reenvio.await())
        assertEquals(ResultadoSeguir.Confirmado(ana.id, false), toque.await())
        assertEquals(listOf(EnvioSeguir(ana.id, true), EnvioSeguir(ana.id, false)), repo.enviados)
        assertTrue(repo.filaAtual.isEmpty())
    }

    @Test
    fun `reenvio para no primeiro sem conexao`() = runTest {
        repo.respostaDoServidor = AppError.SemConexao
        seguir(ana, seguir = true)
        seguir(bruno, seguir = true)
        repo.enviados.clear()

        val r = seguir.reenviarPendentes()

        assertEquals(listOf(ResultadoSeguir.Pendente(ana.id, true, AppError.SemConexao)), r)
        assertEquals(1, repo.enviados.size)
        assertEquals(2, repo.filaAtual.size)
    }

    @Test
    fun `falha definitiva no reenvio reverte`() = runTest {
        repo.respostaDoServidor = AppError.SemConexao
        seguir(ana, seguir = true)

        repo.respostaDoServidor = AppError.NaoAutorizado
        val r = seguir.reenviarPendentes()

        assertEquals(listOf(ResultadoSeguir.Revertido(ana.id, false, AppError.NaoAutorizado)), r)
        assertTrue(repo.filaAtual.isEmpty())
        assertFalse(seguindoAgora())
    }
}
