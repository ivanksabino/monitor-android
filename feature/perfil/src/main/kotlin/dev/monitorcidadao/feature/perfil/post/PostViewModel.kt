package dev.monitorcidadao.feature.perfil.post

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.monitorcidadao.core.routing.Router
import dev.monitorcidadao.domain.PostId
import dev.monitorcidadao.domain.repository.PostsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Placeholder do detalhe do post ("Ver explicação"): título da tela + título do post, se ele já
 * estiver no aparelho (aberto pela lista). Por deep link sem cache mostra só "Post".
 */
class PostViewModel(
    private val id: PostId,
    private val posts: PostsRepository,
    private val router: Router,
) : ViewModel() {
    data class State(val titulo: String? = null, val carregando: Boolean = true)

    sealed interface Action {
        data object Apareceu : Action
        data object TocouVoltar : Action
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private var iniciado = false

    fun send(action: Action) {
        when (action) {
            Action.Apareceu -> if (!iniciado) {
                iniciado = true
                viewModelScope.launch {
                    // Falha ao ler o cache só esconde o título; cancelamento (ViewModel limpo) segue adiante.
                    val titulo = try {
                        posts.post(id)?.titulo
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                    _state.update { it.copy(titulo = titulo, carregando = false) }
                }
            }
            Action.TocouVoltar -> router.pop()
        }
    }
}
