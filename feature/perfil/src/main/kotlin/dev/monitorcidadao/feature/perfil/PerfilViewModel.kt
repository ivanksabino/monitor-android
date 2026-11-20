package dev.monitorcidadao.feature.perfil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.monitorcidadao.core.routing.Route
import dev.monitorcidadao.core.routing.Router
import dev.monitorcidadao.core.routing.Sheet
import dev.monitorcidadao.domain.AppError
import dev.monitorcidadao.domain.Paginator
import dev.monitorcidadao.domain.Pessoa
import dev.monitorcidadao.domain.PessoaId
import dev.monitorcidadao.domain.Post
import dev.monitorcidadao.domain.PostId
import dev.monitorcidadao.domain.StatusPagina
import dev.monitorcidadao.domain.repository.SeguindoRepository
import dev.monitorcidadao.domain.usecase.ObterPerfil
import dev.monitorcidadao.domain.usecase.PerfilCarregado
import dev.monitorcidadao.domain.usecase.ResultadoSeguir
import dev.monitorcidadao.domain.usecase.SeguirPessoa
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Segmentado "Resumo | Em números". */
enum class Segmento { Resumo, EmNumeros }

/** Seções do perfil (pílulas roláveis). Só [Posts] tem conteúdo nesta entrega. */
enum class Secao { Posts, Sobre, Atuacao, Dinheiro, Votos, Imprensa }

/**
 * Perfil de uma pessoa (tela 01). Fluxo em mão única: a View manda [Action] por [send] e desenha [state].
 * Mesmas ações e mesmo `State` do `PerfilViewModel` do iOS.
 *
 * - Perfil: cache primeiro e revalida na rede ([ObterPerfil]); falha sem cache = erro de tela cheia,
 *   com cache = aviso discreto "Mostrando dados salvos".
 * - Posts: [Paginator] (cache + rede, rodapé de carregando/erro/fim).
 * - Seguir: otimista pelo banco local ([SeguirPessoa]); reversão vira [Efeito.FalhaAoSeguir] (a View anuncia).
 */
class PerfilViewModel(
    private val id: PessoaId,
    private val obterPerfil: ObterPerfil,
    private val seguir: SeguirPessoa,
    private val seguindo: SeguindoRepository,
    private val router: Router,
    private val paginator: Paginator<Post>,
) : ViewModel() {

    data class State(
        val pessoa: Pessoa? = null,
        val posts: List<Post> = emptyList(),
        /** Primeira carga sem cache: esqueleto "Carregando perfil". */
        val carregando: Boolean = false,
        /** Falha sem nada para mostrar: tela de erro com "Tentar de novo". */
        val erro: AppError? = null,
        /** Cache na tela enquanto a rede confirma (sem anúncio). */
        val revalidando: Boolean = false,
        /** Offline com cache: aviso discreto. */
        val mostrandoDadosSalvos: Boolean = false,
        val seguindo: Boolean = false,
        val segmento: Segmento = Segmento.Resumo,
        val secao: Secao = Secao.Posts,
        /** Rodapé da lista (carregando mais / erro de página / fim). */
        val paginacao: StatusPagina = StatusPagina.Ocioso,
        /** Puxar para atualizar em andamento. */
        val atualizando: Boolean = false,
    )

    sealed interface Action {
        data object Apareceu : Action
        data class ChegouPerto(val id: PostId) : Action
        data object TocouSeguir : Action
        data class TocouPost(val id: PostId) : Action
        data object PuxouParaAtualizar : Action
        data class TocouSegmento(val segmento: Segmento) : Action
        data class TocouSecao(val secao: Secao) : Action
        /** "Tentar de novo" do erro de tela cheia. */
        data object TocouTentarDeNovo : Action
        /** "Tentar de novo" do rodapé da lista. */
        data object TocouTentarDeNovoPagina : Action
        data object TocouVoltar : Action
        data object TocouCompartilhar : Action
        data object TocouAlertas : Action
    }

    /** Efeitos únicos (não são estado): a View traduz para texto localizado e anuncia (a11y §10). */
    sealed interface Efeito {
        /** O servidor recusou e o botão voltou ao estado anterior. [seguir] = o que a pessoa tentou. */
        data class FalhaAoSeguir(val nome: String, val seguir: Boolean) : Efeito
    }

    private val _state = MutableStateFlow(State(carregando = true))
    val state: StateFlow<State> = _state.asStateFlow()

    private val _efeitos = Channel<Efeito>(Channel.BUFFERED)
    val efeitos: Flow<Efeito> = _efeitos.receiveAsFlow()

    private var iniciado = false
    private var perfilJob: Job? = null

    /**
     * Seguir: o banco emite de forma assíncrona, então o próximo alvo sai da tela (já otimista),
     * não da última emissão. Enquanto houver envio em andamento a emissão do banco não sobrescreve
     * a tela; quando o último termina, a tela volta a refletir o banco (inclusive uma reversão).
     */
    private var enviosSeguir = 0
    private var seguindoNoBanco = false

    fun send(action: Action) {
        when (action) {
            Action.Apareceu -> iniciar()
            is Action.ChegouPerto -> chegouPerto(action.id)
            Action.TocouSeguir -> tocouSeguir()
            is Action.TocouPost -> router.push(Route.Post(action.id))
            Action.PuxouParaAtualizar -> atualizar()
            is Action.TocouSegmento -> _state.update { if (it.segmento == action.segmento) it else it.copy(segmento = action.segmento) }
            is Action.TocouSecao -> _state.update { if (it.secao == action.secao) it else it.copy(secao = action.secao) }
            Action.TocouTentarDeNovo -> tentarDeNovo()
            Action.TocouTentarDeNovoPagina -> viewModelScope.launch { paginator.retry() }
            Action.TocouVoltar -> router.pop()
            Action.TocouCompartilhar -> router.present(Sheet.CompartilharPerfil(id))
            Action.TocouAlertas -> router.present(Sheet.Alertas(id))
        }
    }

    /** Idempotente: a tela pode reaparecer (voltar de um push) sem recarregar. */
    private fun iniciar() {
        if (iniciado) return
        iniciado = true
        observarPerfil(forcarRede = false)
        viewModelScope.launch {
            seguindo.observarSeguindo(id).collect { s ->
                seguindoNoBanco = s
                if (enviosSeguir == 0) _state.update { it.copy(seguindo = s) }
            }
        }
        viewModelScope.launch {
            paginator.state.collect { p -> _state.update { it.copy(posts = p.itens, paginacao = p.status) } }
        }
        viewModelScope.launch { paginator.loadFirst() }
    }

    /** (Re)assina o perfil. [pronto] completa quando a revalidação desta assinatura terminar. */
    private fun observarPerfil(forcarRede: Boolean, pronto: CompletableDeferred<Unit>? = null) {
        perfilJob?.cancel()
        perfilJob = viewModelScope.launch {
            obterPerfil(id, forcarRede).collect { carregado ->
                _state.update { it.com(carregado) }
                if (!carregado.revalidando) pronto?.complete(Unit)
            }
        }
    }

    private fun State.com(c: PerfilCarregado): State = copy(
        pessoa = c.pessoa,
        carregando = c.pessoa == null && c.revalidando,
        erro = if (c.pessoa == null) c.erro else null,
        revalidando = c.pessoa != null && c.revalidando,
        mostrandoDadosSalvos = c.mostrandoDadosSalvos,
    )

    private fun chegouPerto(postId: PostId) {
        val post = _state.value.posts.firstOrNull { it.id == postId } ?: return
        viewModelScope.launch { paginator.loadNextIfNeeded(post) }
    }

    private fun tocouSeguir() {
        val atual = _state.value
        val pessoa = atual.pessoa ?: return
        val alvo = !atual.seguindo
        enviosSeguir++
        _state.update { it.copy(seguindo = alvo) }
        viewModelScope.launch {
            val resultado = try {
                seguir(pessoa.paraResumo(), alvo)
            } finally {
                if (--enviosSeguir == 0) _state.update { it.copy(seguindo = seguindoNoBanco) }
            }
            if (resultado is ResultadoSeguir.Revertido) _efeitos.send(Efeito.FalhaAoSeguir(pessoa.nome, alvo))
        }
    }

    private fun atualizar() {
        if (_state.value.atualizando || _state.value.pessoa == null) return
        _state.update { it.copy(atualizando = true) }
        viewModelScope.launch {
            val perfilPronto = CompletableDeferred<Unit>()
            observarPerfil(forcarRede = true, pronto = perfilPronto)
            paginator.refresh()
            perfilPronto.await()
            _state.update { it.copy(atualizando = false) }
        }
    }

    private fun tentarDeNovo() {
        if (_state.value.erro == null) return
        _state.update { it.copy(erro = null, carregando = true) }
        observarPerfil(forcarRede = false)
        viewModelScope.launch {
            if ((paginator.state.value.status as? StatusPagina.Erro)?.primeiraPagina == true) paginator.retry()
        }
    }
}
