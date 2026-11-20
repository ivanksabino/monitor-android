package dev.monitorcidadao.data.repository

import dev.monitorcidadao.core.network.NetworkError
import dev.monitorcidadao.domain.AppError
import kotlin.math.ceil

/**
 * Tradução `NetworkError` → [AppError], feita no Data (a apresentação nunca vê erro de rede).
 * Mesmas regras do módulo Data do iOS.
 */
fun NetworkError.toAppError(): AppError = when (this) {
    is NetworkError.NoConnection, is NetworkError.Timeout -> AppError.SemConexao
    is NetworkError.Http -> when (status) {
        401, 403 -> AppError.NaoAutorizado
        404, 410 -> AppError.NaoEncontrado
        429 -> AppError.MuitasRequisicoes(retryAfter?.let { ceil(it.inWholeMilliseconds / 1000.0).toInt() })
        400, 422 -> AppError.DadosInvalidos
        in 500..599 -> AppError.ServidorIndisponivel
        else -> AppError.Desconhecido
    }
    is NetworkError.Decoding -> AppError.DadosInvalidos
    is NetworkError.Security, is NetworkError.Cancelled, is NetworkError.Unknown -> AppError.Desconhecido
}
