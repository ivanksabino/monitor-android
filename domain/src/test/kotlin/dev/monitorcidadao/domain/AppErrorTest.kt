package dev.monitorcidadao.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AppErrorTest {
    @Test
    fun `tentar de novo so onde repetir pode resolver (igual ao iOS)`() {
        val permite = listOf(
            AppError.SemConexao, AppError.MuitasRequisicoes(10), AppError.ServidorIndisponivel, AppError.Desconhecido,
        )
        val naoPermite = listOf(AppError.NaoEncontrado, AppError.NaoAutorizado, AppError.DadosInvalidos)
        assertEquals(List(permite.size) { true }, permite.map { it.permiteTentarDeNovo })
        assertEquals(List(naoPermite.size) { false }, naoPermite.map { it.permiteTentarDeNovo })
    }
}
