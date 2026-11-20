package dev.monitorcidadao.core.designsystem.format

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import dev.monitorcidadao.core.designsystem.R
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * Formatadores pt-BR do app: a versão visual (curta, técnica) e a versão para o leitor de tela
 * (por extenso, humana — a11y §7). Números e datas seguem o locale das strings (`ds_locale_tag`),
 * para nunca misturar "R$ 1,234.00" com texto em português.
 */
class MonitorFormat(private val resources: Resources) {
    val locale: Locale = Locale.forLanguageTag(resources.getString(R.string.ds_locale_tag))
    private val symbols = DecimalFormatSymbols.getInstance(locale)
    private val curta = DateTimeFormatter.ofPattern(resources.getString(R.string.ds_data_curta_padrao), locale)
    private val longa = DateTimeFormatter.ofPattern(resources.getString(R.string.ds_data_longa_padrao), locale)

    // DecimalFormat não é thread-safe: a instância vive na composição (main thread), lembrada por configuração.
    private val moedaDecimal = decimal("#,##0.00")
    private val numeroDecimal = decimal("#,##0.#")
    private val moedaInteira = decimal("#,##0")

    /** "08/03" (visual, ao lado do chip do post). */
    fun dataCurta(data: LocalDate): String = curta.format(data)

    /** "8 de março de 2027" (leitor de tela). */
    fun dataLonga(data: LocalDate): String = longa.format(data)

    /** "R$ 112.340,18" (linha de dados mono). */
    fun moedaCompleta(centavos: Long): String =
        resources.getString(R.string.ds_moeda_completa, moedaDecimal.format(reais(centavos)))

    /** "R$ 112 mil", "R$ 8,4 mi" (número de destaque). */
    fun moedaCompacta(centavos: Long): String {
        val (valor, unidade) = MoneyScale.of(centavos)
        val numero = numero(valor)
        return when (unidade) {
            MoneyScale.Unidade.Reais -> resources.getString(R.string.ds_moeda_compacta_reais, numero)
            MoneyScale.Unidade.Mil -> resources.getString(R.string.ds_moeda_compacta_mil, numero)
            MoneyScale.Unidade.Milhao -> resources.getString(R.string.ds_moeda_compacta_milhao, numero)
            MoneyScale.Unidade.Bilhao -> resources.getString(R.string.ds_moeda_compacta_bilhao, numero)
        }
    }

    /** "112 mil reais", "8,4 milhões de reais", "1 milhão de reais" (leitor de tela). */
    fun moedaPorExtenso(centavos: Long): String {
        val (valor, unidade) = MoneyScale.of(centavos)
        val numero = numero(valor)
        // Plural pela parte inteira, sem sinal: "1,2 milhão", "-1 real", e "0 reais" (não "0 real").
        val quantidade = quantidadePlural(valor.abs().toInt())
        return when (unidade) {
            MoneyScale.Unidade.Reais -> resources.getQuantityString(R.plurals.ds_extenso_reais, quantidade, numero)
            MoneyScale.Unidade.Mil -> resources.getString(R.string.ds_extenso_mil_reais, numero)
            MoneyScale.Unidade.Milhao ->
                resources.getQuantityString(R.plurals.ds_extenso_milhoes_reais, quantidade, numero)
            MoneyScale.Unidade.Bilhao ->
                resources.getQuantityString(R.plurals.ds_extenso_bilhoes_reais, quantidade, numero)
        }
    }

    /**
     * Valor em destaque do post (bloco `valor_destaque`): abaixo de R$ 100 mil vai inteiro, com
     * milhar e sem arredondar ("R$ 12.900"; centavos só quando há: "R$ 12.900,50"), igual ao título
     * do post; a partir de R$ 100 mil, compacto ("R$ 112 mil"). Os stat cards seguem com [moedaCompacta].
     * Mesma regra do iOS (`FormatoDinheiro.destaque`).
     */
    fun moedaDestaque(centavos: Long): String =
        if (abs(centavos) < LIMITE_DESTAQUE_COMPACTO) {
            resources.getString(R.string.ds_moeda_completa, numeroInteiro(centavos))
        } else {
            moedaCompacta(centavos)
        }

    /** Leitor de tela, no mesmo critério de [moedaDestaque]: "12.900 reais", "1 real", "112 mil reais". */
    fun moedaDestaquePorExtenso(centavos: Long): String {
        val magnitude = abs(centavos)
        if (magnitude < 100 || magnitude >= LIMITE_DESTAQUE_COMPACTO) return moedaPorExtenso(centavos)
        val quantidade = if (magnitude == 100L) 1 else 2
        return resources.getQuantityString(R.plurals.ds_extenso_reais, quantidade, numeroInteiro(centavos))
    }

    /** "92%". O leitor fala "92 por cento" sozinho. */
    fun percentual(valor: Int): String = resources.getString(R.string.ds_percentual, valor.toString())

    /** "18/20" (visual do card Em números). */
    fun placarPresenca(presencas: List<Boolean>): String =
        resources.getString(R.string.ds_presenca_placar_visual, presencas.count { it }, presencas.size)

    /** "Presente em 18 das últimas 20 sessões; faltou na 6ª e na 15ª" (resumo da grade de presença). */
    fun resumoPresenca(presencas: List<Boolean>): String {
        if (presencas.isEmpty()) return resources.getString(R.string.ds_presenca_sem_dados)
        val presentes = presencas.count { it }
        val base = resources.getQuantityString(R.plurals.ds_presenca_resumo, presencas.size, presentes, presencas.size)
        val faltas = presencas.withIndex().filterNot { it.value }
            .map { resources.getString(R.string.ds_presenca_ordinal, it.index + 1) }
        return if (faltas.isEmpty()) base else resources.getString(R.string.ds_presenca_faltas, base, listaFalada(faltas))
    }

    /** "a", "a e b", "a, b e c". */
    fun listaFalada(itens: List<String>): String = when (itens.size) {
        0 -> ""
        1 -> itens.single()
        else -> itens.dropLast(1).joinToString(resources.getString(R.string.ds_lista_separador)) +
            resources.getString(R.string.ds_lista_ultimo_separador) + itens.last()
    }

    /** Nome do estado pela sigla ("PB" → "Paraíba"); sigla desconhecida volta como veio. */
    fun uf(sigla: String): String {
        val siglas = resources.getStringArray(R.array.ds_uf_siglas)
        val nomes = resources.getStringArray(R.array.ds_uf_nomes)
        val i = siglas.indexOf(sigla.uppercase(Locale.ROOT))
        return if (i >= 0) nomes[i] else sigla
    }

    /**
     * "Deputada federal · PEX · Paraíba" (visual). [cargo] chega já localizado: o DesignSystem não
     * conhece o Domain nem os valores do contrato da API (a feature mapeia o `Cargo` tipado).
     */
    fun subtituloPessoa(cargo: String, partido: String, uf: String): String =
        resources.getString(R.string.ds_pessoa_subtitulo, cargo, partido, uf(uf))

    /** "Ana Ribeiro, Deputada federal, PEX, Paraíba" (nome acessível da linha da pessoa). */
    fun pessoaFalada(nome: String, cargo: String, partido: String, uf: String): String =
        resources.getString(R.string.ds_pessoa_falada, nome, cargo, partido, uf(uf))

    /** "Cota parlamentar em 2027: 112 mil reais". */
    fun rotuloValor(rotulo: String, valor: String): String = resources.getString(R.string.ds_rotulo_valor, rotulo, valor)

    /** "12.900" (ponto de milhar); "12.900,50" só quando há centavos. */
    private fun numeroInteiro(centavos: Long): String =
        (if (centavos % 100 == 0L) moedaInteira else moedaDecimal).format(reais(centavos))

    private fun reais(centavos: Long): BigDecimal = BigDecimal.valueOf(centavos).movePointLeft(2)

    private fun numero(valor: BigDecimal): String = numeroDecimal.format(valor)

    private fun decimal(pattern: String) = DecimalFormat(pattern, symbols).apply { roundingMode = RoundingMode.HALF_UP }

    private companion object {
        /** R$ 100 mil, em centavos: daqui para cima o destaque do post fica compacto. */
        const val LIMITE_DESTAQUE_COMPACTO = 10_000_000L
    }
}

/**
 * Quantidade para escolher o plural em pt-BR. O CLDR do Android põe 0 em "one" ("0 abstenção");
 * em português falado zero é plural ("0 abstenções"). Use sempre que [n] puder ser zero.
 */
fun quantidadePlural(n: Int): Int = if (n == 0) 2 else n

/** Formatador do contexto atual (recriado se a configuração/idioma mudar). */
@Composable
fun rememberMonitorFormat(): MonitorFormat {
    val resources = LocalResources.current
    val configuration = LocalConfiguration.current
    return remember(resources, configuration) { MonitorFormat(resources) }
}

/**
 * Escala de dinheiro compartilhada pela versão visual e pela falada (mesmo arredondamento nas duas):
 * abaixo de mil em reais inteiros; mil com 1 casa até 10 mil; milhão/bilhão com 1 casa até 100.
 * Arredondar para 1000 da unidade sobe de unidade ("999,96 mil" vira "1 milhão").
 */
internal object MoneyScale {
    enum class Unidade(val divisor: BigDecimal) {
        Reais(BigDecimal.ONE),
        Mil(BigDecimal("1000")),
        Milhao(BigDecimal("1000000")),
        Bilhao(BigDecimal("1000000000")),
    }

    data class Escalado(val valor: BigDecimal, val unidade: Unidade)

    private val MIL = BigDecimal("1000")

    fun of(centavos: Long): Escalado {
        val reais = BigDecimal.valueOf(centavos).movePointLeft(2).abs()
        var unidade = Unidade.entries.last { reais >= it.divisor || it == Unidade.Reais }
        while (true) {
            val arredondado = round(reais.divide(unidade.divisor), unidade)
            val proxima = Unidade.entries.getOrNull(unidade.ordinal + 1)
            if (arredondado >= MIL && proxima != null) {
                unidade = proxima
            } else {
                val sinal = if (centavos < 0) arredondado.negate() else arredondado
                return Escalado(sinal.stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it }, unidade)
            }
        }
    }

    private fun round(valor: BigDecimal, unidade: Unidade): BigDecimal {
        val casas = when (unidade) {
            Unidade.Reais -> 0
            Unidade.Mil -> if (valor < BigDecimal.TEN) 1 else 0
            Unidade.Milhao, Unidade.Bilhao -> if (valor < BigDecimal("100")) 1 else 0
        }
        return valor.setScale(casas, RoundingMode.HALF_UP)
    }
}
