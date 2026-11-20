package dev.monitorcidadao.domain.usecase

import dev.monitorcidadao.domain.AppError
import dev.monitorcidadao.domain.AppException
import dev.monitorcidadao.domain.Pessoa
import dev.monitorcidadao.domain.PessoaId
import dev.monitorcidadao.domain.repository.PessoaRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** O que a tela de Perfil precisa saber a cada momento. */
data class PerfilCarregado(
    /** `null` enquanto não há cache nem resposta da rede. */
    val pessoa: Pessoa?,
    val revalidando: Boolean,
    /** Falha da última revalidação (com ou sem cache na tela). */
    val erro: AppError?,
) {
    /** Offline com cache: a tela mostra o aviso discreto "Mostrando dados salvos". */
    val mostrandoDadosSalvos: Boolean get() = pessoa != null && erro != null
}

/**
 * Regra: cache primeiro, revalida na rede (stale-while-revalidate).
 * - Emite o que houver no banco imediatamente (sem esperar a rede).
 * - Em paralelo busca na rede; o banco é atualizado e o novo valor chega pelo mesmo fluxo.
 * - Falha da rede não apaga o cache: vira [PerfilCarregado.erro].
 */
class ObterPerfil(private val pessoas: PessoaRepository) {

    operator fun invoke(id: PessoaId, forcarRede: Boolean = false): Flow<PerfilCarregado> = channelFlow {
        val revalidacao = MutableStateFlow<Revalidacao>(Revalidacao.EmAndamento)
        launch {
            revalidacao.value = try {
                pessoas.revalidar(id, forcarRede)
                Revalidacao.Ok
            } catch (e: CancellationException) {
                throw e
            } catch (e: AppException) {
                Revalidacao.Falhou(e.erro)
            } catch (_: Exception) {
                // Defesa: o repositório promete AppException, mas uma falha inesperada (ex.: banco)
                // não pode derrubar o fluxo da tela.
                Revalidacao.Falhou(AppError.Desconhecido)
            }
        }
        combine(pessoas.observar(id), revalidacao) { pessoa, r ->
            PerfilCarregado(
                pessoa = pessoa,
                revalidando = r == Revalidacao.EmAndamento,
                erro = (r as? Revalidacao.Falhou)?.erro,
            )
        }.distinctUntilChanged().collectLatest { send(it) }
    }

    private sealed interface Revalidacao {
        data object EmAndamento : Revalidacao
        data object Ok : Revalidacao
        data class Falhou(val erro: AppError) : Revalidacao
    }
}
