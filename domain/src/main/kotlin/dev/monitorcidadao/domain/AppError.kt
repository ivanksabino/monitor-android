package dev.monitorcidadao.domain

/**
 * Erro que o domínio e as telas entendem, já traduzido da rede/banco pelo Data.
 * A View escolhe a mensagem localizada a partir do caso. Mesmos casos do `AppError` do iOS.
 */
sealed interface AppError {
    data object SemConexao : AppError
    data object NaoEncontrado : AppError

    /** 429. [tenteEmSegundos] vem do `Retry-After`, quando o servidor mandou. */
    data class MuitasRequisicoes(val tenteEmSegundos: Int?) : AppError
    data object ServidorIndisponivel : AppError
    data object NaoAutorizado : AppError
    data object DadosInvalidos : AppError
    data object Desconhecido : AppError

    /** Vale oferecer "Tentar de novo". */
    val permiteTentarDeNovo: Boolean
        get() = when (this) {
            SemConexao, is MuitasRequisicoes, ServidorIndisponivel, Desconhecido -> true
            NaoEncontrado, NaoAutorizado, DadosInvalidos -> false
        }
}

/** Como um [AppError] atravessa uma função `suspend` (repositórios lançam, use cases e ViewModels pegam). */
class AppException(val erro: AppError, cause: Throwable? = null) : Exception(erro.toString(), cause)
