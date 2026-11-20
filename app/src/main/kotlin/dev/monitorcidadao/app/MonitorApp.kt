package dev.monitorcidadao.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.defaultPopTransitionSpec
import androidx.navigation3.ui.defaultPredictivePopTransitionSpec
import androidx.navigation3.ui.defaultTransitionSpec
import dev.monitorcidadao.core.designsystem.Monitor
import dev.monitorcidadao.core.designsystem.MonitorRadius
import dev.monitorcidadao.core.designsystem.MonitorSize
import dev.monitorcidadao.core.designsystem.MonitorSpacing
import dev.monitorcidadao.core.designsystem.MonitorType
import dev.monitorcidadao.core.designsystem.a11y.LocalReduceMotion
import dev.monitorcidadao.core.designsystem.a11y.isAccessibilityFontScale
import dev.monitorcidadao.core.designsystem.icon.MonitorIcons
import dev.monitorcidadao.core.routing.Route
import dev.monitorcidadao.core.routing.Router
import dev.monitorcidadao.core.routing.Sheet
import dev.monitorcidadao.core.routing.Tab
import kotlinx.coroutines.launch

/**
 * Casca do app: barra de abas própria (cresce com a fonte, a11y §13) com 5 abas, cada uma com seu [Router] e seu
 * `NavDisplay` (Navigation 3), e o host da sheet da aba atual.
 * Raiz com `testTagsAsResourceId` para o Trail/uiautomator enxergarem os testTags (a11y §17).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MonitorApp(navigation: AppNavigation, factory: AppFactory, modifier: Modifier = Modifier) {
    val colors = Monitor.colors
    val saveable = rememberSaveableStateHolder()
    Scaffold(
        modifier = modifier.fillMaxSize().semantics { testTagsAsResourceId = true },
        containerColor = colors.canvas,
        bottomBar = { BarraDeAbas(navigation.selecionada, onSelecionar = navigation::selecionar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            val tab = navigation.selecionada
            // Só a aba atual fica na composição (e na árvore de a11y); o estado das outras é guardado.
            key(tab) {
                saveable.SaveableStateProvider(tab.name) {
                    PilhaDaAba(navigation.router(tab), factory)
                }
                // Deep link: a pilha só é trocada com o NavDisplay desta aba já composto, para o
                // Navigation 3 limpar o ViewModel e o estado salvo das entradas que saem.
                val pendente = navigation.linkPendente
                LaunchedEffect(pendente) { if (pendente != null) navigation.aplicarLinkPendente(tab) }
            }
        }
    }
    // Na raiz de outra aba, voltar leva ao Início antes de sair do app.
    BackHandler(enabled = navigation.selecionada != Tab.Inicio && navigation.atual.backStack.size == 1) {
        navigation.selecionar(Tab.Inicio)
    }
    navigation.atual.sheet?.let { SheetHost(it, navigation.atual, factory) }
}

@Composable
private fun PilhaDaAba(router: Router, factory: AppFactory) {
    val reduceMotion = LocalReduceMotion.current
    NavDisplay(
        backStack = router.backStack,
        onBack = { router.pop() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        // Reduzir movimento (a11y §15): sem transição entre telas.
        transitionSpec = if (reduceMotion) ({ EnterTransition.None togetherWith ExitTransition.None }) else defaultTransitionSpec(),
        popTransitionSpec = if (reduceMotion) ({ EnterTransition.None togetherWith ExitTransition.None }) else defaultPopTransitionSpec(),
        predictivePopTransitionSpec = if (reduceMotion) {
            { _ -> EnterTransition.None togetherWith ExitTransition.None }
        } else {
            defaultPredictivePopTransitionSpec()
        },
        entryProvider = { key: NavKey -> NavEntry(key) { factory.make(it as Route, router) } },
    )
}

/** Altura mínima da barra (a do Material 3); com fonte grande ela cresce em vez de cortar o rótulo. */
private val AlturaMinimaDaBarra = 80.dp

/**
 * Barra de abas própria: o `NavigationBar` do Material 3 tem altura fixa e cortava o rótulo em
 * fontScale 2.0 (a11y §13, WCAG 1.4.4). Contrato de abas (a11y §5): `selectableGroup()` no
 * contêiner e cada item `selectable(role = Role.Tab)`. Selecionado: cor, pílula atrás do ícone e
 * peso 700 (cor nunca sozinha, a11y §14). Tocar na aba já selecionada volta à raiz dela.
 */
@Composable
internal fun BarraDeAbas(selecionada: Tab, onSelecionar: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val colors = Monitor.colors
    val fonteGrande = isAccessibilityFontScale()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
            .drawBehind {
                drawLine(colors.border, Offset(0f, 0f), Offset(size.width, 0f), MonitorSize.cardBorder.toPx())
            }
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(IntrinsicSize.Min)
            .selectableGroup(),
    ) {
        Tab.entries.forEach { tab ->
            val selected = tab == selecionada
            val cor = if (selected) colors.accent else colors.muted
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .heightIn(min = AlturaMinimaDaBarra)
                    .testTag(tab.testTag)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelecionar(tab) })
                    .padding(horizontal = 2.dp, vertical = MonitorSpacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MonitorSpacing.xs, Alignment.CenterVertically),
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(MonitorRadius.pill))
                        .background(if (selected) colors.segmentTrack else Color.Transparent)
                        .padding(horizontal = if (fonteGrande) MonitorSpacing.md else 20.dp, vertical = MonitorSpacing.xs),
                ) {
                    // Em fonte de acessibilidade o ícone encolhe para sobrar espaço ao rótulo.
                    Icon(
                        tab.icone(),
                        contentDescription = null,
                        tint = cor,
                        modifier = Modifier.size(if (fonteGrande) MonitorSize.icon else 24.dp),
                    )
                }
                // Sem maxLines: o rótulo quebra (com hifenização) e a barra cresce, nunca corta.
                Text(
                    text = stringResource(tab.rotulo()),
                    style = MonitorType.caption.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        hyphens = Hyphens.Auto,
                    ),
                    color = cor,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun Tab.icone(): ImageVector = when (this) {
    Tab.Inicio -> MonitorIcons.Inicio
    Tab.Explorar -> MonitorIcons.Explorar
    Tab.Entenda -> MonitorIcons.Entenda
    Tab.Participe -> MonitorIcons.Participe
    Tab.Voce -> MonitorIcons.Voce
}

private fun Tab.rotulo(): Int = when (this) {
    Tab.Inicio -> R.string.tab_inicio
    Tab.Explorar -> R.string.tab_explorar
    Tab.Entenda -> R.string.tab_entenda
    Tab.Participe -> R.string.tab_participe
    Tab.Voce -> R.string.tab_voce
}

/**
 * Sheet da aba atual. `ModalBottomSheet` prende o foco (a11y §11); ao fechar, a tela que abriu
 * devolve o foco ao gatilho depois que a sheet sai da composição (a11y §4).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun SheetHost(sheet: Sheet, router: Router, factory: AppFactory) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = { router.dismissSheet() },
        sheetState = state,
        containerColor = Monitor.colors.surface,
        // A sheet é outra janela (dialog): o testTagsAsResourceId da raiz do app não chega aqui.
        // Sem isto os testTags da sheet (alertas_titulo, alertas_fechar…) não viram resource-id
        // para o uiautomator/Trail (BUG-004, reteste r2). Vale para toda sheet do app.
        modifier = Modifier.semantics { testTagsAsResourceId = true },
    ) {
        factory.makeSheet(sheet) {
            scope.launch { state.hide() }.invokeOnCompletion { router.dismissSheet() }
        }
    }
}
