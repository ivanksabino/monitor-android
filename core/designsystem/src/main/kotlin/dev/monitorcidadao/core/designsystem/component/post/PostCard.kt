package dev.monitorcidadao.core.designsystem.component.post

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.monitorcidadao.core.designsystem.Monitor
import dev.monitorcidadao.core.designsystem.MonitorRadius
import dev.monitorcidadao.core.designsystem.MonitorSize
import dev.monitorcidadao.core.designsystem.MonitorSpacing
import dev.monitorcidadao.core.designsystem.MonitorTouch
import dev.monitorcidadao.core.designsystem.MonitorType
import dev.monitorcidadao.core.designsystem.R
import dev.monitorcidadao.core.designsystem.a11y.a11yElement
import dev.monitorcidadao.core.designsystem.a11y.a11yHeading
import dev.monitorcidadao.core.designsystem.a11y.decorative
import dev.monitorcidadao.core.designsystem.component.Chip
import dev.monitorcidadao.core.designsystem.component.ChipTone
import dev.monitorcidadao.core.designsystem.format.MonitorFormat
import dev.monitorcidadao.core.designsystem.format.quantidadePlural
import dev.monitorcidadao.core.designsystem.format.rememberMonitorFormat
import dev.monitorcidadao.core.designsystem.icon.MonitorIcons

/** testTags estáveis do card (spec da tela 01). */
object PostCardTags {
    fun card(id: String) = "post_card_$id"
    fun explicacao(id: String) = "post_explicacao_$id"
    const val DATA = "post_data"
}

/**
 * Renderer de post (casca + blocos). Tipo desconhecido vira [GenericPostCard]; bloco desconhecido é ignorado.
 *
 * A11y (tela 01 §9): chip e data não são paradas próprias; o título é cabeçalho com label composto
 * "Apresentou projeto, 8 de março de 2027. <título>"; corpo é texto; o link leva o contexto
 * ("Ver explicação: <título>"). O card em si não é focável (tem controle dentro).
 */
@Composable
fun PostCard(
    model: PostCardModel,
    onVerExplicacao: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (PostCardLayout.isGeneric(model)) {
        GenericPostCard(model, modifier)
        return
    }
    PostCardShell(model, modifier) { parte -> PostParte(model, parte, onVerExplicacao) }
}

/** Card para tipo desconhecido: data, título, corpo e fonte. O app não quebra com tipo novo da API. */
@Composable
fun GenericPostCard(model: PostCardModel, modifier: Modifier = Modifier) {
    PostCardShell(model, modifier) { parte -> PostParte(model, parte, onVerExplicacao = {}) }
}

@Composable
private fun PostCardShell(
    model: PostCardModel,
    modifier: Modifier,
    content: @Composable (PostCardParte) -> Unit,
) {
    val colors = Monitor.colors
    val shape = RoundedCornerShape(MonitorRadius.lg)
    Column(
        modifier = modifier
            .testTag(PostCardTags.card(model.id))
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(MonitorSize.cardBorder, colors.border, shape)
            .padding(MonitorSpacing.lg),
    ) {
        PostCardLayout.partes(model).forEachIndexed { index, parte ->
            if (index > 0) Spacer(Modifier.height(parte.espacoAntes()))
            content(parte)
        }
    }
}

/** Espaço acima de cada parte (protótipo): título a 10, corpo a 6, o resto a 8. */
private fun PostCardParte.espacoAntes(): Dp = when (this) {
    PostCardParte.Titulo -> 10.dp
    PostCardParte.Corpo -> 6.dp
    else -> MonitorSpacing.sm
}

@Composable
private fun PostParte(model: PostCardModel, parte: PostCardParte, onVerExplicacao: (String) -> Unit) {
    val format = rememberMonitorFormat()
    val colors = Monitor.colors
    when (parte) {
        PostCardParte.Cabecalho -> PostCabecalho(
            tipo = tipoLabel(model.tipo),
            tone = model.tipo.tone(),
            data = format.dataCurta(model.publicadoEm),
            // Chip e data saem da árvore: tipo e data já estão no título falado.
            modifier = Modifier.fillMaxWidth().decorative(),
        )
        PostCardParte.Data -> Text(
            text = format.dataCurta(model.publicadoEm),
            style = MonitorType.caption,
            color = colors.muted,
            modifier = Modifier.decorative(),
        )
        PostCardParte.Titulo -> Text(
            text = model.titulo,
            style = MonitorType.cardTitle,
            color = colors.ink,
            modifier = Modifier.a11yHeading(postTitleLabel(model, format)),
        )
        PostCardParte.Corpo -> Text(text = model.corpo, style = MonitorType.body, color = colors.body)
        is PostCardParte.Bloco -> PostBlocoView(parte.bloco, format)
        PostCardParte.VerExplicacao -> VerExplicacaoLink(model, onVerExplicacao)
    }
}

/**
 * Chip do tipo + data curta, numa linha (chip à esquerda, data à direita) quando os dois cabem inteiros.
 * Em fonte grande (200%) o chip ocupa quase a largura toda e a data desce INTEIRA para a linha de baixo,
 * em vez de quebrar caractere a caractere ou ser cortada (a11y §13). Layout próprio porque o FlowRow
 * mede o último item com o espaço que sobra e espreme a data na mesma linha.
 */
@Composable
internal fun PostCabecalho(tipo: String, tone: ChipTone, data: String, modifier: Modifier = Modifier) {
    val gap = MonitorSpacing.sm
    val linhaGap = MonitorSpacing.xs
    Layout(
        modifier = modifier,
        content = {
            Chip(text = tipo, tone = tone, fontWeight = FontWeight.Bold)
            Text(
                text = data,
                style = MonitorType.caption,
                color = Monitor.colors.muted,
                softWrap = false,
                modifier = Modifier.testTag(PostCardTags.DATA),
            )
        },
    ) { measurables, constraints ->
        val solto = constraints.copy(minWidth = 0, minHeight = 0)
        val chip = measurables[0].measure(solto)
        val dataPlaceable = measurables[1].measure(solto)
        val largura = if (constraints.hasBoundedWidth) constraints.maxWidth else chip.width + gap.roundToPx() + dataPlaceable.width
        val umaLinha = chip.width + gap.roundToPx() + dataPlaceable.width <= largura
        val altura = if (umaLinha) {
            maxOf(chip.height, dataPlaceable.height)
        } else {
            chip.height + linhaGap.roundToPx() + dataPlaceable.height
        }
        layout(largura, altura.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            if (umaLinha) {
                chip.placeRelative(0, (altura - chip.height) / 2)
                dataPlaceable.placeRelative(largura - dataPlaceable.width, (altura - dataPlaceable.height) / 2)
            } else {
                chip.placeRelative(0, 0)
                dataPlaceable.placeRelative(0, chip.height + linhaGap.roundToPx())
            }
        }
    }
}

@Composable
private fun tipoLabel(tipo: PostTipo): String = when (tipo) {
    PostTipo.Apresentou -> stringResource(R.string.ds_post_tipo_apresentou)
    PostTipo.Votou -> stringResource(R.string.ds_post_tipo_votou)
    PostTipo.Gastou -> stringResource(R.string.ds_post_tipo_gastou)
    PostTipo.Discursou -> stringResource(R.string.ds_post_tipo_discursou)
    PostTipo.Desconhecido -> ""
}

private fun PostTipo.tone(): ChipTone = when (this) {
    PostTipo.Votou -> ChipTone.Accent
    PostTipo.Gastou -> ChipTone.Gasto
    else -> ChipTone.Neutral
}

/** "Apresentou projeto, 8 de março de 2027. Quer UBS…" — genérico: "8 de março de 2027. <título>". */
@Composable
internal fun postTitleLabel(model: PostCardModel, format: MonitorFormat): String {
    val data = format.dataLonga(model.publicadoEm)
    return if (PostCardLayout.isGeneric(model)) {
        stringResource(R.string.ds_post_titulo_generico_a11y, data, model.titulo)
    } else {
        stringResource(R.string.ds_post_titulo_a11y, tipoLabel(model.tipo), data, model.titulo)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PostBlocoView(bloco: PostBloco, format: MonitorFormat) {
    val colors = Monitor.colors
    when (bloco) {
        is PostBloco.Placar -> {
            val falado = format.listaFalada(
                listOf(
                    pluralStringResource(R.plurals.ds_placar_sim, quantidadePlural(bloco.sim), bloco.sim),
                    pluralStringResource(R.plurals.ds_placar_nao, quantidadePlural(bloco.nao), bloco.nao),
                    pluralStringResource(R.plurals.ds_placar_abstencao, quantidadePlural(bloco.abstencao), bloco.abstencao),
                ),
            )
            // Cor reforça, o texto diz ("Sim", "Não"): cor nunca sozinha (a11y §14).
            FlowRow(
                modifier = Modifier.a11yElement(stringResource(R.string.ds_placar_a11y, falado)),
                horizontalArrangement = Arrangement.spacedBy(MonitorSpacing.md),
            ) {
                val numero = MonitorType.label
                Text(stringResource(R.string.ds_placar_visual_sim, bloco.sim), style = numero, color = colors.ink)
                Text(stringResource(R.string.ds_placar_visual_nao, bloco.nao), style = numero, color = colors.signalNao)
                Text(
                    stringResource(R.string.ds_placar_visual_abstencao, bloco.abstencao),
                    style = numero,
                    color = colors.muted,
                )
            }
        }
        is PostBloco.ValorDestaque -> Column(
            modifier = Modifier.a11yElement(format.rotuloValor(bloco.rotulo, format.moedaDestaquePorExtenso(bloco.centavos))),
        ) {
            Text(format.moedaDestaque(bloco.centavos), style = MonitorType.stat, color = colors.signalGasto)
            Text(bloco.rotulo, style = MonitorType.caption, color = colors.muted)
        }
        is PostBloco.Fonte -> Text(
            text = stringResource(R.string.ds_post_fonte, bloco.texto),
            style = MonitorType.caption,
            color = colors.muted,
        )
        is PostBloco.Desconhecido -> Unit // ignorado (contrato da API)
    }
}

@Composable
private fun VerExplicacaoLink(model: PostCardModel, onVerExplicacao: (String) -> Unit) {
    val colors = Monitor.colors
    val label = stringResource(R.string.ds_post_ver_explicacao_a11y, model.titulo)
    Row(
        modifier = Modifier
            .testTag(PostCardTags.explicacao(model.id))
            .defaultMinSize(minHeight = MonitorTouch.minTarget)
            .clickable(role = Role.Button) { onVerExplicacao(model.id) }
            // testTag, nome e papel no MESMO nó; texto e chevron fora da árvore (BUG-006).
            .clearAndSetSemantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(MonitorSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.ds_post_ver_explicacao),
            style = MonitorType.label.copy(fontWeight = FontWeight.Bold),
            color = colors.accent,
        )
        Icon(
            MonitorIcons.ChevronDireita,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(MonitorSize.iconSmall),
        )
    }
}
