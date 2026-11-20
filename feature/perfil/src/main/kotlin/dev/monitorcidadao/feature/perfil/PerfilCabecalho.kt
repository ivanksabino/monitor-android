package dev.monitorcidadao.feature.perfil

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.monitorcidadao.core.designsystem.Monitor
import dev.monitorcidadao.core.designsystem.MonitorSize
import dev.monitorcidadao.core.designsystem.MonitorSpacing
import dev.monitorcidadao.core.designsystem.MonitorType
import dev.monitorcidadao.core.designsystem.a11y.LocalScreenReaderEnabled
import dev.monitorcidadao.core.designsystem.a11y.isAccessibilityFontScale
import dev.monitorcidadao.core.designsystem.component.Avatar
import dev.monitorcidadao.core.designsystem.component.ChipModel
import dev.monitorcidadao.core.designsystem.component.ChipTone
import dev.monitorcidadao.core.designsystem.component.CircleIconButton
import dev.monitorcidadao.core.designsystem.component.HeadingLevel
import dev.monitorcidadao.core.designsystem.component.SecondaryButton
import dev.monitorcidadao.core.designsystem.component.SectionHeader
import dev.monitorcidadao.core.designsystem.component.StatusChipsGroup
import dev.monitorcidadao.core.designsystem.component.ToggleButton
import dev.monitorcidadao.core.designsystem.format.rememberMonitorFormat
import dev.monitorcidadao.core.designsystem.icon.MonitorIcons
import dev.monitorcidadao.core.routing.Sheet
import dev.monitorcidadao.domain.Pessoa
import dev.monitorcidadao.feature.perfil.PerfilViewModel.Action
import dev.monitorcidadao.feature.perfil.PerfilViewModel.State
import kotlinx.coroutines.delay

private val CapaAltura = 150.dp
private val AvatarSobreposicao = 46.dp

/** Botões que abrem sheet e recebem o foco de volta quando ela fecha (a11y §4). */
internal class FocosDeRetorno {
    val alertas = FocusRequester()
    val compartilhar = FocusRequester()
}

/** Última sheet vista (não é estado de UI: mudar não recompõe). */
private class UltimaSheet(var valor: Sheet?)

/**
 * Ao fechar a sheet (escolhendo ou cancelando), devolve o foco ao gatilho — só com leitor de tela
 * ativo e depois que a sheet saiu da composição (um foco imediato se perde na animação).
 */
@Composable
internal fun rememberFocosDeRetorno(sheetAberta: Sheet?): FocosDeRetorno {
    val focos = remember { FocosDeRetorno() }
    val ultima = remember { UltimaSheet(null) }
    val leitorAtivo = LocalScreenReaderEnabled.current
    LaunchedEffect(sheetAberta) {
        val fechada = ultima.valor
        ultima.valor = sheetAberta
        if (sheetAberta != null || fechada == null || !leitorAtivo) return@LaunchedEffect
        delay(FOCO_APOS_SHEET_MS)
        val alvo = when (fechada) {
            is Sheet.Alertas -> focos.alertas
            is Sheet.CompartilharPerfil -> focos.compartilhar
            else -> null
        }
        // O botão pode ter saído da tela (lista rolada): sem alvo, o foco fica onde o sistema pôs.
        alvo?.let { runCatching { it.requestFocus() } }
    }
    return focos
}

/** Espera a animação de saída da sheet terminar antes de mover o foco. */
private const val FOCO_APOS_SHEET_MS = 350L

/** Alvo de foco programático (exceção do a11y §4): focável também em modo toque. */
private fun Modifier.focoDeRetorno(requester: FocusRequester): Modifier =
    focusRequester(requester).focusProperties { canFocus = true }

@Composable
internal fun PerfilCabecalho(pessoa: Pessoa, state: State, send: (Action) -> Unit, focos: FocosDeRetorno) {
    val colors = Monitor.colors
    val format = rememberMonitorFormat()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .drawBehind {
                val y = size.height - 0.5.dp.toPx()
                drawLine(colors.border, Offset(0f, y), Offset(size.width, y), MonitorSize.cardBorder.toPx())
            }
            .padding(bottom = MonitorSpacing.lg),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Capa(Modifier.fillMaxWidth().height(CapaAltura))
            Row(
                modifier = Modifier.fillMaxWidth().padding(MonitorSpacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                BotaoVoltar(send)
                CircleIconButton(
                    icon = MonitorIcons.Compartilhar,
                    contentDescription = stringResource(R.string.perfil_compartilhar_a11y, pessoa.nome),
                    onClick = { send(Action.TocouCompartilhar) },
                    modifier = Modifier.focoDeRetorno(focos.compartilhar).testTag(PerfilTags.COMPARTILHAR),
                )
            }
            Avatar(
                iniciais = pessoa.iniciais,
                size = MonitorSize.avatar,
                borderWidth = 4.dp,
                modifier = Modifier.padding(start = MonitorSpacing.lg, top = CapaAltura - AvatarSobreposicao),
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = MonitorSpacing.lg).padding(top = MonitorSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(MonitorSpacing.xs),
        ) {
            // O gesto de puxar para atualizar não existe no TalkBack: a mesma ação fica no menu de
            // ações do h1, um nó focável que está sempre na tela (o LazyColumn não recebe foco).
            val atualizar = stringResource(R.string.perfil_atualizar)
            SectionHeader(
                text = pessoa.nome,
                level = HeadingLevel.H1,
                modifier = Modifier
                    .testTag(PerfilTags.NOME)
                    .semantics {
                        customActions = listOf(
                            CustomAccessibilityAction(atualizar) {
                                send(Action.PuxouParaAtualizar)
                                true
                            },
                        )
                    },
            )
            Text(
                text = format.subtituloPessoa(pessoa.cargo.texto(), pessoa.partido, pessoa.uf),
                style = MonitorType.body,
                color = colors.body,
            )
            StatusChipsGroup(chips = chipsDeStatus(pessoa), modifier = Modifier.padding(top = 6.dp))
            if (state.mostrandoDadosSalvos) AvisoDadosSalvos(Modifier.padding(top = MonitorSpacing.sm))
            BotoesDeAcao(pessoa, state.seguindo, send, focos, Modifier.padding(top = 10.dp))
        }
    }
}

@Composable
private fun chipsDeStatus(pessoa: Pessoa): List<ChipModel> = listOf(
    if (pessoa.emExercicio) {
        ChipModel(stringResource(R.string.perfil_status_em_exercicio), ChipTone.Accent)
    } else {
        ChipModel(stringResource(R.string.perfil_status_fora_de_exercicio))
    },
    ChipModel(stringResource(R.string.perfil_status_mandato, pessoa.mandato.inicio, pessoa.mandato.fim)),
    ChipModel(stringResource(R.string.perfil_status_numero_mandato, pessoa.mandato.numero)),
)

/** Seguir (toggle, label fixo) e Alertas lado a lado; em fonte de acessibilidade, um embaixo do outro. */
@Composable
private fun BotoesDeAcao(
    pessoa: Pessoa,
    seguindo: Boolean,
    send: (Action) -> Unit,
    focos: FocosDeRetorno,
    modifier: Modifier,
) {
    val seguir: @Composable (Modifier) -> Unit = { m ->
        ToggleButton(
            checked = seguindo,
            onCheckedChange = { send(Action.TocouSeguir) },
            textOff = stringResource(R.string.perfil_seguir),
            textOn = stringResource(R.string.perfil_seguindo),
            accessibilityLabel = stringResource(R.string.perfil_seguir_a11y, pessoa.nome),
            stateOn = stringResource(R.string.perfil_estado_seguindo),
            stateOff = stringResource(R.string.perfil_estado_nao_seguindo),
            modifier = m.testTag(PerfilTags.SEGUIR),
        )
    }
    val alertas: @Composable (Modifier) -> Unit = { m ->
        SecondaryButton(
            text = stringResource(R.string.perfil_alertas),
            icon = MonitorIcons.Sino,
            onClick = { send(Action.TocouAlertas) },
            modifier = m.focoDeRetorno(focos.alertas).testTag(PerfilTags.ALERTAS),
        )
    }
    if (isAccessibilityFontScale()) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MonitorSpacing.sm)) {
            seguir(Modifier.fillMaxWidth())
            alertas(Modifier.fillMaxWidth())
        }
    } else {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MonitorSpacing.sm)) {
            seguir(Modifier.weight(1f))
            alertas(Modifier.weight(1f))
        }
    }
}

/** Offline com cache: aviso discreto, UM elemento (texto simples). */
@Composable
private fun AvisoDadosSalvos(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.perfil_dados_salvos),
        style = MonitorType.caption,
        color = Monitor.colors.body,
        modifier = modifier
            .testTag(PerfilTags.DADOS_SALVOS)
            .clip(RoundedCornerShape(MonitorSpacing.sm))
            .background(Monitor.colors.canvas)
            .padding(horizontal = 10.dp, vertical = MonitorSpacing.xs),
    )
}

/** Capa decorativa: cor accent com listras diagonais sutis (sem semântica, fora da árvore). */
@Composable
private fun Capa(modifier: Modifier) {
    val accent = Monitor.colors.accent
    Box(
        modifier = modifier
            .clipToBounds()
            .background(accent)
            .drawBehind {
                val passo = 18.dp.toPx()
                val traco = 2.dp.toPx()
                val listra = Color.White.copy(alpha = 0.08f)
                var x = -size.height
                while (x < size.width) {
                    drawLine(listra, Offset(x, size.height), Offset(x + size.height, 0f), traco)
                    x += passo
                }
            },
    )
}
