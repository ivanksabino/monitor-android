package dev.monitorcidadao.feature.perfil

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.monitorcidadao.core.designsystem.Monitor
import dev.monitorcidadao.core.designsystem.MonitorRadius
import dev.monitorcidadao.core.designsystem.MonitorSpacing
import dev.monitorcidadao.core.designsystem.MonitorType
import dev.monitorcidadao.core.designsystem.a11y.LocalA11yAnnouncer
import dev.monitorcidadao.core.designsystem.component.CircleIconButton
import dev.monitorcidadao.core.designsystem.component.HeadingLevel
import dev.monitorcidadao.core.designsystem.component.ListFooter
import dev.monitorcidadao.core.designsystem.component.ListFooterState
import dev.monitorcidadao.core.designsystem.component.PrimaryButton
import dev.monitorcidadao.core.designsystem.component.SectionHeader
import dev.monitorcidadao.core.designsystem.component.SectionTabs
import dev.monitorcidadao.core.designsystem.component.SegmentedTabs
import dev.monitorcidadao.core.designsystem.component.SkeletonBlock
import dev.monitorcidadao.core.designsystem.component.SkeletonView
import dev.monitorcidadao.core.designsystem.component.TabOption
import dev.monitorcidadao.core.designsystem.component.VisuallyHiddenHeading
import dev.monitorcidadao.core.designsystem.component.post.PostCard
import dev.monitorcidadao.core.designsystem.icon.MonitorIcons
import dev.monitorcidadao.core.routing.Sheet
import dev.monitorcidadao.domain.AppError
import dev.monitorcidadao.domain.StatusPagina
import dev.monitorcidadao.feature.perfil.PerfilViewModel.Action
import dev.monitorcidadao.feature.perfil.PerfilViewModel.Efeito
import dev.monitorcidadao.feature.perfil.PerfilViewModel.State

/** Identificadores de teste da tela 01 (separados do label, a11y §17). */
object PerfilTags {
    const val VOLTAR = "perfil_voltar"
    const val COMPARTILHAR = "perfil_compartilhar"
    const val NOME = "perfil_nome"
    const val SEGUIR = "perfil_seguir"
    const val ALERTAS = "perfil_alertas"
    const val SEGMENTO_RESUMO = "perfil_segmento_resumo"
    const val SEGMENTO_NUMEROS = "perfil_segmento_numeros"
    const val PRESENCA = "perfil_presenca"
    const val LISTA = "perfil_lista"
    const val CARREGANDO = "perfil_carregando"
    const val ERRO_TENTAR = "perfil_erro_tentar"
    const val DADOS_SALVOS = "perfil_dados_salvos"
    const val TITULO_OCULTO = "perfil_titulo"

    /** `perfil_stat_presenca`, `perfil_stat_projetos`, `perfil_stat_cota`, `perfil_stat_emendas`. */
    fun stat(chave: String) = "perfil_stat_$chave"

    /** `perfil_secao_posts`, `perfil_secao_sobre`, ... */
    fun secao(secao: Secao) = "perfil_secao_${secao.chave}"
}

internal val Secao.chave: String
    get() = when (this) {
        Secao.Posts -> "posts"
        Secao.Sobre -> "sobre"
        Secao.Atuacao -> "atuacao"
        Secao.Dinheiro -> "dinheiro"
        Secao.Votos -> "votos"
        Secao.Imprensa -> "imprensa"
    }

/**
 * Liga o [PerfilViewModel] à tela: assina o estado, dispara `Apareceu` e anuncia os efeitos.
 * [sheetAberta] é a sheet do Router da aba: ao fechar, o foco volta ao botão que a abriu (a11y §4).
 */
@Composable
fun PerfilRoute(viewModel: PerfilViewModel, sheetAberta: Sheet?) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val announcer = LocalA11yAnnouncer.current
    val resources = LocalResources.current
    LaunchedEffect(viewModel) { viewModel.send(Action.Apareceu) }
    LaunchedEffect(viewModel) {
        viewModel.efeitos.collect { efeito ->
            when (efeito) {
                // Transição de estado que o foco não revela sozinho: o botão voltou ao estado anterior.
                is Efeito.FalhaAoSeguir -> announcer.announce(
                    resources.getString(
                        if (efeito.seguir) R.string.perfil_seguir_falhou else R.string.perfil_deixar_de_seguir_falhou,
                        efeito.nome,
                    ),
                )
            }
        }
    }
    PerfilScreen(state = state, send = viewModel::send, sheetAberta = sheetAberta)
}

/** Tela burra: desenha [state] e manda ações por [send]. */
@Composable
fun PerfilScreen(
    state: State,
    send: (Action) -> Unit,
    modifier: Modifier = Modifier,
    sheetAberta: Sheet? = null,
) {
    // paneTitle: com Navigation 3 tudo roda na mesma janela; o painel novo avisa o TalkBack da troca
    // de tela (TYPE_WINDOW_STATE_CHANGED), que recomeça a leitura pelo primeiro controle (Voltar, a11y §1).
    val tituloDoPainel = state.pessoa?.nome
        ?: stringResource(if (state.erro != null) R.string.perfil_titulo_tela else R.string.perfil_carregando)
    Box(
        modifier
            .fillMaxSize()
            .background(Monitor.colors.canvas)
            .semantics { paneTitle = tituloDoPainel },
    ) {
        val erro = state.erro
        when {
            state.pessoa != null -> PerfilConteudo(state, send, sheetAberta)
            erro != null -> PerfilErro(erro, send)
            else -> PerfilCarregando(send)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PerfilConteudo(state: State, send: (Action) -> Unit, sheetAberta: Sheet?) {
    val pessoa = state.pessoa ?: return
    val focos = rememberFocosDeRetorno(sheetAberta)
    PullToRefreshBox(
        isRefreshing = state.atualizando,
        onRefresh = { send(Action.PuxouParaAtualizar) },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag(PerfilTags.LISTA),
            contentPadding = PaddingValues(bottom = MonitorSpacing.lg),
        ) {
            item(key = "cabecalho") { PerfilCabecalho(pessoa, state, send, focos) }
            item(key = "segmentado") { Segmentado(state.segmento, send) }
            item(key = "segmento") {
                when (state.segmento) {
                    Segmento.Resumo -> ResumoGrade(pessoa)
                    Segmento.EmNumeros -> EmNumerosCard(pessoa)
                }
            }
            item(key = "secoes") { Secoes(state.secao, send) }
            if (state.secao == Secao.Posts) {
                item(key = "posts_titulo") { VisuallyHiddenHeading(stringResource(R.string.perfil_secao_posts)) }
                items(state.posts, key = { it.id.valor }) { post ->
                    // Paginação: cada card que entra pede a próxima página; o Paginator só busca perto do fim.
                    LaunchedEffect(post.id) { send(Action.ChegouPerto(post.id)) }
                    val model = remember(post) { post.paraCardModel() }
                    PostCard(
                        model = model,
                        onVerExplicacao = { send(Action.TocouPost(post.id)) },
                        modifier = Modifier.padding(horizontal = MonitorSpacing.md).padding(top = MonitorSpacing.sm),
                    )
                }
                item(key = "rodape") {
                    ListFooter(
                        state = state.paginacao.paraRodape(),
                        onRetry = { send(Action.TocouTentarDeNovoPagina) },
                        loadingLabel = stringResource(
                            if (state.paginacao == StatusPagina.CarregandoPrimeira) {
                                R.string.perfil_posts_carregando_primeira
                            } else {
                                R.string.perfil_posts_carregando
                            },
                        ),
                        errorText = stringResource(R.string.perfil_posts_erro),
                        retryText = stringResource(R.string.perfil_tentar_de_novo),
                        endText = stringResource(R.string.perfil_posts_fim),
                    )
                }
            } else {
                item(key = "em_breve_${state.secao.chave}") { SecaoEmBreve(state.secao) }
            }
        }
    }
}

@Composable
private fun Segmentado(segmento: Segmento, send: (Action) -> Unit) {
    val opcoes = listOf(
        TabOption(stringResource(R.string.perfil_segmento_resumo), PerfilTags.SEGMENTO_RESUMO),
        TabOption(stringResource(R.string.perfil_segmento_numeros), PerfilTags.SEGMENTO_NUMEROS),
    )
    val segmentos = Segmento.entries
    SegmentedTabs(
        options = opcoes,
        selectedIndex = segmentos.indexOf(segmento),
        onSelect = { send(Action.TocouSegmento(segmentos[it])) },
        modifier = Modifier.padding(horizontal = MonitorSpacing.md).padding(top = MonitorSpacing.md),
    )
}

@Composable
private fun Secoes(secao: Secao, send: (Action) -> Unit) {
    val secoes = Secao.entries
    SectionTabs(
        options = secoes.map { TabOption(secaoLabel(it), PerfilTags.secao(it)) },
        selectedIndex = secoes.indexOf(secao),
        onSelect = { send(Action.TocouSecao(secoes[it])) },
        modifier = Modifier.padding(horizontal = MonitorSpacing.md).padding(top = MonitorSpacing.md),
    )
}

@Composable
private fun secaoLabel(secao: Secao): String = stringResource(
    when (secao) {
        Secao.Posts -> R.string.perfil_secao_posts
        Secao.Sobre -> R.string.perfil_secao_sobre
        Secao.Atuacao -> R.string.perfil_secao_atuacao
        Secao.Dinheiro -> R.string.perfil_secao_dinheiro
        Secao.Votos -> R.string.perfil_secao_votos
        Secao.Imprensa -> R.string.perfil_secao_imprensa
    },
)

/** Seções ainda sem conteúdo: título h2 + "Em breve". */
@Composable
private fun SecaoEmBreve(secao: Secao) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(MonitorSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(MonitorSpacing.xs),
    ) {
        SectionHeader(text = secaoLabel(secao), level = HeadingLevel.H2)
        Text(stringResource(R.string.perfil_secao_em_breve), style = MonitorType.body, color = Monitor.colors.body)
    }
}

private fun StatusPagina.paraRodape(): ListFooterState = when (this) {
    StatusPagina.Ocioso -> ListFooterState.Ocioso
    StatusPagina.CarregandoPrimeira, StatusPagina.CarregandoMais -> ListFooterState.Carregando
    is StatusPagina.Erro -> ListFooterState.Erro
    StatusPagina.Fim -> ListFooterState.Fim
}

/** Primeira carga sem cache: Voltar continua disponível; esqueleto é UM elemento "Carregando perfil". */
@Composable
private fun PerfilCarregando(send: (Action) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        BotaoVoltar(send, Modifier.padding(MonitorSpacing.md))
        TituloOculto()
        SkeletonView(
            label = stringResource(R.string.perfil_carregando),
            modifier = Modifier.fillMaxWidth().padding(horizontal = MonitorSpacing.lg).testTag(PerfilTags.CARREGANDO),
        ) {
            SkeletonBlock(Modifier.size(96.dp), radius = MonitorRadius.pill * 3)
            SkeletonBlock(Modifier.padding(top = MonitorSpacing.lg).fillMaxWidth(0.7f).height(32.dp))
            SkeletonBlock(Modifier.padding(top = MonitorSpacing.sm).fillMaxWidth(0.5f).height(18.dp))
            SkeletonBlock(Modifier.padding(top = MonitorSpacing.lg).fillMaxWidth().height(48.dp))
            SkeletonBlock(Modifier.padding(top = MonitorSpacing.lg).fillMaxWidth().height(160.dp), radius = MonitorRadius.card)
        }
    }
}

/** Erro sem cache: mensagem (anunciada pela live region ao aparecer) + "Tentar de novo" quando faz sentido. */
@Composable
private fun PerfilErro(erro: AppError, send: (Action) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        BotaoVoltar(send, Modifier.padding(MonitorSpacing.md))
        TituloOculto()
        Column(
            modifier = Modifier.fillMaxWidth().padding(MonitorSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(MonitorSpacing.lg),
        ) {
            Text(
                text = stringResource(erro.mensagem()),
                style = MonitorType.body,
                color = Monitor.colors.ink,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (erro.permiteTentarDeNovo) {
                PrimaryButton(
                    text = stringResource(R.string.perfil_tentar_de_novo),
                    onClick = { send(Action.TocouTentarDeNovo) },
                    modifier = Modifier.fillMaxWidth().testTag(PerfilTags.ERRO_TENTAR),
                )
            }
        }
    }
}

/** h1 "Perfil" só para o leitor nos estados sem nome (carregando, erro): a tela diz o que é. */
@Composable
private fun TituloOculto() {
    VisuallyHiddenHeading(stringResource(R.string.perfil_titulo_tela), Modifier.testTag(PerfilTags.TITULO_OCULTO))
}

@Composable
internal fun BotaoVoltar(send: (Action) -> Unit, modifier: Modifier = Modifier) {
    CircleIconButton(
        icon = MonitorIcons.Voltar,
        contentDescription = stringResource(R.string.perfil_voltar),
        onClick = { send(Action.TocouVoltar) },
        modifier = modifier.testTag(PerfilTags.VOLTAR),
    )
}

private fun AppError.mensagem(): Int = when (this) {
    AppError.SemConexao -> R.string.perfil_erro_sem_conexao
    AppError.NaoEncontrado -> R.string.perfil_erro_nao_encontrado
    is AppError.MuitasRequisicoes -> R.string.perfil_erro_muitas_requisicoes
    else -> R.string.perfil_erro_generico
}
