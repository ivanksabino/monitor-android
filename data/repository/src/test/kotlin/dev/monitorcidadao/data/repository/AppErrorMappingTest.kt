package dev.monitorcidadao.data.repository

import dev.monitorcidadao.core.network.NetworkError
import dev.monitorcidadao.domain.AppError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class AppErrorMappingTest {
    @Test
    fun `sem rede e tempo esgotado viram SemConexao`() {
        assertEquals(AppError.SemConexao, NetworkError.NoConnection().toAppError())
        assertEquals(AppError.SemConexao, NetworkError.Timeout().toAppError())
    }

    @ParameterizedTest
    @CsvSource("401,NaoAutorizado", "403,NaoAutorizado", "404,NaoEncontrado", "400,DadosInvalidos", "500,ServidorIndisponivel", "503,ServidorIndisponivel", "418,Desconhecido")
    fun `status HTTP vira o caso certo`(status: Int, esperado: String) {
        assertEquals(esperado, NetworkError.Http(status).toAppError()::class.simpleName)
    }

    @Test
    fun `429 leva o Retry-After arredondado para cima`() {
        assertEquals(AppError.MuitasRequisicoes(30), NetworkError.Http(429, retryAfter = 30.seconds).toAppError())
        assertEquals(AppError.MuitasRequisicoes(2), NetworkError.Http(429, retryAfter = 1500.milliseconds).toAppError())
        assertEquals(AppError.MuitasRequisicoes(null), NetworkError.Http(429).toAppError())
    }

    @Test
    fun `decodificacao vira DadosInvalidos e o resto Desconhecido`() {
        assertEquals(AppError.DadosInvalidos, NetworkError.Decoding(IllegalArgumentException()).toAppError())
        assertEquals(AppError.Desconhecido, NetworkError.Security(IOException()).toAppError())
        assertEquals(AppError.Desconhecido, NetworkError.Cancelled().toAppError())
        assertEquals(AppError.Desconhecido, NetworkError.Unknown(IllegalStateException()).toAppError())
    }
}
