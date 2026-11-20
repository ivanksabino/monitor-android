package dev.monitorcidadao.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.monitorcidadao.core.routing.Route
import dev.monitorcidadao.core.routing.Router
import dev.monitorcidadao.domain.AppError
import dev.monitorcidadao.domain.AppException
import dev.monitorcidadao.domain.PessoaId
import dev.monitorcidadao.domain.PessoaResumo
import dev.monitorcidadao.domain.repository.SeguindoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Aba Início, nesta entrega: "Quem você segue". A lista vem do banco local (seguidos ficam só no
 * aparelho); na primeira abertura importa `GET /v1/seguindo`. Sem use case: só lista.
 */
class FeedViewModel(
    private val seguindo: SeguindoRepository,
    private val router: Router,
) : ViewModel() {

    data class State(
        val pessoas: List<PessoaResumo> = emptyList(),
        /**
         * Até o banco entregar a 1ª leitura E a importação terminar. "Vazio" só depois que o banco
         * confirmou a lista vazia (BUG-010: com a importação já feita, ela terminava antes da 1ª
         * emissão do Room e a tela caía em "Você ainda não segue ninguém").
         */
        val carregando: Boolean = true,
        /** Falha da importação com a lista vazia (com itens, a lista local basta). */
        val erro: AppError? = null,
    )

    sealed interface Action {
        data object Apareceu : Action
        data class TocouPessoa(val id: PessoaId) : Action
        data object TocouTentarDeNovo : Action
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private var iniciado = false
    private var leuBanco = false
    private var importando = false

    fun send(action: Action) {
        when (action) {
            Action.Apareceu -> iniciar()
            is Action.TocouPessoa -> router.push(Route.Perfil(action.id))
            Action.TocouTentarDeNovo -> importar()
        }
    }

    private fun iniciar() {
        if (iniciado) return
        iniciado = true
        viewModelScope.launch {
            seguindo.observarSeguidos().collect { lista ->
                leuBanco = true
                _state.update { it.copy(pessoas = lista, carregando = importando, erro = it.erro.takeIf { lista.isEmpty() }) }
            }
        }
        importar()
    }

    private fun importar() {
        importando = true
        _state.update { it.copy(carregando = true, erro = null) }
        viewModelScope.launch {
            val erro = try {
                seguindo.importarSeNecessario()
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: AppException) {
                e.erro
            }
            importando = false
            _state.update { it.copy(carregando = !leuBanco, erro = erro.takeIf { _ -> it.pessoas.isEmpty() }) }
        }
    }
}
