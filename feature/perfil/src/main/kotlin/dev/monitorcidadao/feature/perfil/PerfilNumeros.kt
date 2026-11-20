package dev.monitorcidadao.feature.perfil

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.monitorcidadao.core.designsystem.Monitor
import dev.monitorcidadao.core.designsystem.MonitorRadius
import dev.monitorcidadao.core.designsystem.MonitorSpacing
import dev.monitorcidadao.core.designsystem.MonitorType
import dev.monitorcidadao.core.designsystem.a11y.a11yElement
import dev.monitorcidadao.core.designsystem.a11y.decorative
import dev.monitorcidadao.core.designsystem.component.DataRow
import dev.monitorcidadao.core.designsystem.component.HeadingLevel
import dev.monitorcidadao.core.designsystem.component.PresenceGrid
import dev.monitorcidadao.core.designsystem.component.SectionHeader
import dev.monitorcidadao.core.designsystem.component.StatCard
import dev.monitorcidadao.core.designsystem.component.VisuallyHiddenHeading
import dev.monitorcidadao.core.designsystem.format.quantidadePlural
import dev.monitorcidadao.core.designsystem.format.rememberMonitorFormat
import dev.monitorcidadao.core.designsystem.layout.AdaptiveGrid
import dev.monitorcidadao.domain.Pessoa

@Immutable
private data class StatItem(val chave: String, val valor: String, val legenda: String, val falado: String)

/**
 * Resumo: grade 2×2 (1 coluna em fonte de acessibilidade). Cada card é UM elemento com texto humano
 * ("112 mil reais da cota em 2027"). Título h2 só para o leitor ("Resumo do mandato em 2027").
 */
@Composable
internal fun ResumoGrade(pessoa: Pessoa) {
    val format = rememberMonitorFormat()
    val r = pessoa.resumo
    val presenca = format.percentual(r.presencaPct)
    val projetos = r.projetosApresentados
    val itens = listOf(
        StatItem(
            chave = "presenca",
            valor = presenca,
            legenda = stringResource(R.string.perfil_stat_presenca_legenda),
            falado = stringResource(R.string.perfil_stat_presenca_a11y, presenca),
        ),
        StatItem(
            chave = "projetos",
            valor = projetos.toString(),
            legenda = pluralStringResource(R.plurals.perfil_stat_projetos_legenda, quantidadePlural(projetos)),
            falado = pluralStringResource(R.plurals.perfil_stat_projetos_a11y, quantidadePlural(projetos), projetos),
        ),
        StatItem(
            chave = "cota",
            valor = format.moedaCompacta(r.cota.centavos),
            legenda = stringResource(R.string.perfil_stat_cota_legenda, r.ano),
            falado = stringResource(R.string.perfil_stat_cota_a11y, format.moedaPorExtenso(r.cota.centavos), r.ano),
        ),
        StatItem(
            chave = "emendas",
            valor = format.moedaCompacta(r.emendasIndicadas.centavos),
            legenda = stringResource(R.string.perfil_stat_emendas_legenda),
            falado = stringResource(R.string.perfil_stat_emendas_a11y, format.moedaPorExtenso(r.emendasIndicadas.centavos)),
        ),
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = MonitorSpacing.md).padding(top = MonitorSpacing.sm)) {
        VisuallyHiddenHeading(stringResource(R.string.perfil_resumo_titulo, r.ano))
        AdaptiveGrid(items = itens) { item, modifier ->
            StatCard(
                value = item.valor,
                caption = item.legenda,
                accessibilityLabel = item.falado,
                modifier = modifier.testTag(PerfilTags.stat(item.chave)),
            )
        }
    }
}

/**
 * Em números: card escuro, fonte mono. h2 só para o leitor ("Em números do mandato em 2027", no mesmo
 * nível do h2 do Resumo); o overline é o título da presença ("Presença nas últimas 20 sessões"); a grade
 * é UMA imagem com resumo; cada linha chave/valor é UM elemento com o nome humano da chave técnica.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EmNumerosCard(pessoa: Pessoa) {
    val colors = Monitor.colors
    val format = rememberMonitorFormat()
    val n = pessoa.numeros
    val ano = pessoa.resumo.ano
    val sessoes = n.presencaUltimas.size
    Column(Modifier.fillMaxWidth()) {
        VisuallyHiddenHeading(
            stringResource(R.string.perfil_numeros_titulo, ano),
            Modifier.padding(horizontal = MonitorSpacing.md),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MonitorSpacing.md)
                .padding(top = MonitorSpacing.sm)
                .clip(RoundedCornerShape(MonitorRadius.lg))
                .background(colors.dataBg)
                .padding(MonitorSpacing.lg),
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(MonitorSpacing.xs),
            ) {
                SectionHeader(
                    text = stringResource(R.string.perfil_numeros_presenca_overline, sessoes),
                    level = HeadingLevel.Overline,
                    color = colors.dataMuted,
                    accessibilityLabel = stringResource(R.string.perfil_numeros_presenca_titulo, sessoes),
                    modifier = Modifier.padding(end = MonitorSpacing.md),
                )
                // O placar visual "18/20" já está no resumo falado da grade.
                Text(format.placarPresenca(n.presencaUltimas), style = MonitorType.data, color = colors.dataInk, modifier = Modifier.decorative())
            }
            PresenceGrid(n.presencaUltimas, Modifier.padding(top = 10.dp).testTag(PerfilTags.PRESENCA))
            Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(MonitorSpacing.sm)) {
                DataRow(
                    chave = stringResource(R.string.perfil_numeros_cota_chave, ano),
                    valor = format.moedaCompleta(n.cota.centavos),
                    accessibilityLabel = stringResource(R.string.perfil_numeros_cota_a11y, ano, format.moedaPorExtenso(n.cota.centavos)),
                )
                DataRow(
                    chave = stringResource(R.string.perfil_numeros_emendas_chave),
                    valor = stringResource(
                        R.string.perfil_numeros_emendas_valor,
                        format.moedaCompacta(n.emendasPagas.centavos),
                        format.moedaCompacta(n.emendasIndicadas.centavos),
                    ),
                    accessibilityLabel = stringResource(
                        R.string.perfil_numeros_emendas_a11y,
                        format.moedaPorExtenso(n.emendasPagas.centavos),
                        format.moedaPorExtenso(n.emendasIndicadas.centavos),
                    ),
                )
                DataRow(
                    chave = stringResource(R.string.perfil_numeros_patrimonio_chave, n.patrimonioTse.ano),
                    valor = format.moedaCompleta(n.patrimonioTse.valor.centavos),
                    accessibilityLabel = stringResource(
                        R.string.perfil_numeros_patrimonio_a11y,
                        n.patrimonioTse.ano,
                        format.moedaPorExtenso(n.patrimonioTse.valor.centavos),
                    ),
                )
                DataRow(
                    chave = stringResource(R.string.perfil_numeros_votou_chave),
                    valor = format.percentual(n.votouComPartidoPct),
                    accessibilityLabel = stringResource(R.string.perfil_numeros_votou_a11y, format.percentual(n.votouComPartidoPct)),
                )
            }
            if (n.fontes.isNotEmpty()) {
                val visual = n.fontes.joinToString(stringResource(R.string.perfil_numeros_fontes_separador)) { it.lowercase(format.locale) }
                Text(
                    text = stringResource(R.string.perfil_numeros_fontes, visual),
                    style = MonitorType.data.copy(fontSize = MonitorType.caption.fontSize * (11f / 13f)),
                    color = colors.dataMuted,
                    modifier = Modifier
                        .padding(top = MonitorSpacing.md)
                        .a11yElement(stringResource(R.string.perfil_numeros_fontes_a11y, format.listaFalada(n.fontes))),
                )
            }
        }
    }
}
