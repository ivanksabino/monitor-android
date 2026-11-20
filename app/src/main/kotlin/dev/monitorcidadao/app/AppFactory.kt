package dev.monitorcidadao.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.monitorcidadao.core.analytics.AnalyticsEvent
import dev.monitorcidadao.core.analytics.Tela
import dev.monitorcidadao.core.designsystem.Monitor
import dev.monitorcidadao.core.designsystem.MonitorSpacing
import dev.monitorcidadao.core.designsystem.MonitorType
import dev.monitorcidadao.core.designsystem.component.HeadingLevel
import dev.monitorcidadao.core.designsystem.component.PlaceholderScreen
import dev.monitorcidadao.core.designsystem.component.PrimaryButton
import dev.monitorcidadao.core.designsystem.component.SectionHeader
import dev.monitorcidadao.core.routing.Route
import dev.monitorcidadao.core.routing.Router
import dev.monitorcidadao.core.routing.Sheet
import dev.monitorcidadao.domain.PessoaId
import dev.monitorcidadao.domain.PostId
import dev.monitorcidadao.feature.ajustes.AjustesFactory
import dev.monitorcidadao.feature.ajustes.AjustesScreen
import dev.monitorcidadao.feature.entenda.EntendaFactory
import dev.monitorcidadao.feature.entenda.EntendaScreen
import dev.monitorcidadao.feature.explorar.ExplorarFactory
import dev.monitorcidadao.feature.explorar.ExplorarScreen
import dev.monitorcidadao.feature.feed.FeedFactory
import dev.monitorcidadao.feature.feed.FeedRoute
import dev.monitorcidadao.feature.feed.FeedViewModel
import dev.monitorcidadao.feature.participe.ParticipeFactory
import dev.monitorcidadao.feature.participe.ParticipeScreen
import dev.monitorcidadao.feature.perfil.PerfilFactory
import dev.monitorcidadao.feature.perfil.PerfilRoute
import dev.monitorcidadao.feature.perfil.PerfilViewModel
import dev.monitorcidadao.feature.perfil.post.PostRoute
import dev.monitorcidadao.feature.perfil.post.PostViewModel

/** testTags da casca do app. */
object AppTags {
    const val SHEET_TITULO = "sheet_titulo"
    const val SHEET_FECHAR = "sheet_fechar"

    /** Sheet de Alertas: mesmos identificadores do iOS (`AlertasView`). */
    const val ALERTAS_TITULO = "alertas_titulo"
    const val ALERTAS_FECHAR = "alertas_fechar"
    const val ROTA_EM_CONSTRUCAO = "rota_em_construcao"
}

/**
 * Implementa as factories de todas as features (arquitetura §4): é o único lugar, junto com o
 * [AppContainer], que conhece implementações concretas, e o único `when` de [Route] → tela.
 * Cada ViewModel nasce no `ViewModelStore` da entrada da pilha (decorator do Navigation 3).
 */
class AppFactory(private val deps: AppDependencies) :
    FeedFactory, PerfilFactory, ExplorarFactory, EntendaFactory, ParticipeFactory, AjustesFactory {

    @Composable
    fun make(route: Route, router: Router) {
        route.tela()?.let { tela -> RegistrarTelaVista(tela) }
        when (route) {
            Route.Inicio -> makeFeed(router)
            Route.Explorar -> makeExplorar(router)
            Route.Entenda -> makeEntenda(router)
            Route.Participe -> makeParticipe(router)
            Route.Voce -> makeAjustes(router)
            is Route.Perfil -> makePerfil(route.id, router)
            is Route.Post -> makePost(route.id, router)
            // Destinos que ainda não têm feature.
            is Route.Tema, Route.Veiculos -> PlaceholderScreen(
                title = stringResource(R.string.rota_em_construcao_titulo),
                message = stringResource(R.string.rota_em_construcao),
                titleTag = AppTags.ROTA_EM_CONSTRUCAO,
            )
        }
    }

    /**
     * Uma `TelaVista` por entrada da pilha: a guarda fica no estado salvo da entrada (decorator do
     * Navigation 3), que sobrevive à rotação e à troca de aba e é descartado quando a entrada sai.
     */
    @Composable
    private fun RegistrarTelaVista(tela: Tela) {
        var registrada by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            if (!registrada) {
                registrada = true
                deps.analytics.track(AnalyticsEvent.TelaVista(tela))
            }
        }
    }

    @Composable
    override fun makeFeed(router: Router) {
        val viewModel = viewModel(key = "feed") { FeedViewModel(deps.seguindoRepository, router) }
        FeedRoute(viewModel)
    }

    @Composable
    override fun makePerfil(id: PessoaId, router: Router) {
        val viewModel = viewModel(key = "perfil:${id.valor}") {
            PerfilViewModel(
                id = id,
                obterPerfil = deps.obterPerfil,
                seguir = deps.seguirPessoa,
                seguindo = deps.seguindoRepository,
                router = router,
                paginator = deps.postsRepository.postsDaPessoa(id),
            )
        }
        PerfilRoute(viewModel, sheetAberta = router.sheet)
    }

    @Composable
    override fun makePost(id: PostId, router: Router) {
        val viewModel = viewModel(key = "post:${id.valor}") { PostViewModel(id, deps.postsRepository, router) }
        PostRoute(viewModel)
    }

    @Composable
    override fun makeExplorar(router: Router) = ExplorarScreen()

    @Composable
    override fun makeEntenda(router: Router) = EntendaScreen()

    @Composable
    override fun makeParticipe(router: Router) = ParticipeScreen()

    @Composable
    override fun makeAjustes(router: Router) = AjustesScreen()

    /** Conteúdo das sheets (placeholders desta entrega). [fechar] anima a saída e avisa o Router. */
    @Composable
    fun makeSheet(sheet: Sheet, fechar: () -> Unit) {
        val (titulo, texto) = when (sheet) {
            is Sheet.Alertas -> R.string.sheet_alertas_titulo to R.string.sheet_alertas_texto
            is Sheet.CompartilharPerfil, is Sheet.Compartilhar -> R.string.sheet_compartilhar_titulo to R.string.sheet_compartilhar_texto
            Sheet.Filtro -> R.string.sheet_filtro_titulo to R.string.sheet_filtro_texto
        }
        val (tagTitulo, tagFechar) = when (sheet) {
            is Sheet.Alertas -> AppTags.ALERTAS_TITULO to AppTags.ALERTAS_FECHAR
            else -> AppTags.SHEET_TITULO to AppTags.SHEET_FECHAR
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = MonitorSpacing.lg)
                .padding(bottom = MonitorSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(MonitorSpacing.md),
        ) {
            SectionHeader(
                text = stringResource(titulo),
                level = HeadingLevel.H2,
                modifier = Modifier.testTag(tagTitulo),
            )
            Text(stringResource(texto), style = MonitorType.body, color = Monitor.colors.body)
            PrimaryButton(
                text = stringResource(R.string.sheet_fechar),
                onClick = fechar,
                modifier = Modifier.fillMaxWidth().testTag(tagFechar),
            )
        }
    }

    private fun Route.tela(): Tela? = when (this) {
        Route.Inicio -> Tela.Inicio
        Route.Explorar -> Tela.Explorar
        Route.Entenda -> Tela.Entenda
        Route.Participe -> Tela.Participe
        Route.Voce -> Tela.Voce
        is Route.Perfil -> Tela.Perfil
        is Route.Post -> Tela.Post
        is Route.Tema, Route.Veiculos -> null
    }
}
