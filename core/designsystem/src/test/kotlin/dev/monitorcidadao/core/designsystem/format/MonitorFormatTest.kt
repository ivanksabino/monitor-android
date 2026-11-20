package dev.monitorcidadao.core.designsystem.format

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.LocalDate

/** Formatadores com as strings reais do módulo (Robolectric carrega `res/values`). */
@RunWith(RobolectricTestRunner::class)
class MonitorFormatTest {
    private val format = MonitorFormat(RuntimeEnvironment.getApplication().resources)

    @Test
    fun `datas curta e longa em pt-BR`() {
        val data = LocalDate.of(2027, 3, 8)
        assertEquals("08/03", format.dataCurta(data))
        assertEquals("8 de março de 2027", format.dataLonga(data))
    }

    @Test
    fun `pessoa no visual e na fala, com cargo e UF por extenso`() {
        assertEquals("Deputada federal · PEX · Paraíba", format.subtituloPessoa("Deputada federal", "PEX", "PB"))
        assertEquals("Ana Ribeiro, Deputada federal, PEX, Paraíba", format.pessoaFalada("Ana Ribeiro", "Deputada federal", "PEX", "PB"))
        assertEquals("São Paulo", format.uf("sp"))
        assertEquals("XX", format.uf("XX"))
    }

    @Test
    fun `moeda por extenso abreviado para o leitor`() {
        assertEquals("112 mil reais", format.moedaPorExtenso(11_234_018))
        assertEquals("8,4 milhões de reais", format.moedaPorExtenso(840_000_000))
        assertEquals("3,1 milhões de reais", format.moedaPorExtenso(310_000_000))
        assertEquals("1 milhão de reais", format.moedaPorExtenso(100_000_000))
        assertEquals("1,2 milhão de reais", format.moedaPorExtenso(120_000_000))
        assertEquals("2,5 bilhões de reais", format.moedaPorExtenso(250_000_000_000))
        assertEquals("8,5 mil reais", format.moedaPorExtenso(850_000))
        assertEquals("950 reais", format.moedaPorExtenso(95_000))
        assertEquals("1 real", format.moedaPorExtenso(100))
        assertEquals("0 reais", format.moedaPorExtenso(0))
    }

    @Test
    fun `moeda negativa escolhe o plural pelo valor absoluto`() {
        assertEquals("-1 real", format.moedaPorExtenso(-100))
        assertEquals("-2 reais", format.moedaPorExtenso(-200))
        assertEquals("-1,2 milhão de reais", format.moedaPorExtenso(-120_000_000))
    }

    @Test
    fun `resumo de presenca sem sessoes tem frase propria`() {
        assertEquals("Sem sessões registradas", format.resumoPresenca(emptyList()))
    }

    @Test
    fun `quantidade plural trata zero como plural em pt-BR`() {
        assertEquals(2, quantidadePlural(0))
        assertEquals(1, quantidadePlural(1))
        assertEquals(7, quantidadePlural(7))
    }

    @Test
    fun `moeda visual compacta e completa`() {
        assertEquals("R$ 112 mil", format.moedaCompacta(11_234_018))
        assertEquals("R$ 8,4 mi", format.moedaCompacta(840_000_000))
        assertEquals("R$ 1,2 bi", format.moedaCompacta(120_000_000_000))
        assertEquals("R$ 950", format.moedaCompacta(95_000))
        assertEquals("R$ 112.340,18", format.moedaCompleta(11_234_018))
        assertEquals("R$ 1.240.000,00", format.moedaCompleta(124_000_000))
    }

    @Test
    fun `destaque do post abaixo de 100 mil vai inteiro com milhar (BUG-008)`() {
        assertEquals("R$ 12.900", format.moedaDestaque(1_290_000))
        assertEquals("R$ 38.420", format.moedaDestaque(3_842_000))
        assertEquals("R$ 99.999", format.moedaDestaque(9_999_900))
        assertEquals("R$ 12.900,50", format.moedaDestaque(1_290_050))
        assertEquals("R$ 950", format.moedaDestaque(95_000))
        assertEquals("12.900 reais", format.moedaDestaquePorExtenso(1_290_000))
        assertEquals("38.420 reais", format.moedaDestaquePorExtenso(3_842_000))
        assertEquals("1 real", format.moedaDestaquePorExtenso(100))
        assertEquals("0 reais", format.moedaDestaquePorExtenso(0))
    }

    @Test
    fun `destaque do post a partir de 100 mil fica compacto`() {
        assertEquals("R$ 100 mil", format.moedaDestaque(10_000_000))
        assertEquals("R$ 112 mil", format.moedaDestaque(11_234_018))
        assertEquals("R$ 8,4 mi", format.moedaDestaque(840_000_000))
        assertEquals("100 mil reais", format.moedaDestaquePorExtenso(10_000_000))
        assertEquals("8,4 milhões de reais", format.moedaDestaquePorExtenso(840_000_000))
        // Os stat cards do Resumo continuam compactos abaixo de 100 mil.
        assertEquals("R$ 13 mil", format.moedaCompacta(1_290_000))
    }

    @Test
    fun `percentual e placar de presenca`() {
        val presencas = List(20) { it != 5 && it != 14 }
        assertEquals("92%", format.percentual(92))
        assertEquals("18/20", format.placarPresenca(presencas))
    }

    @Test
    fun `resumo da presenca cita as faltas por ordinal`() {
        val presencas = List(20) { it != 5 && it != 14 }
        assertEquals(
            "Presente em 18 das últimas 20 sessões; faltou na 6ª e na 15ª",
            format.resumoPresenca(presencas),
        )
        assertEquals("Presente em 20 das últimas 20 sessões", format.resumoPresenca(List(20) { true }))
        assertEquals(
            "Presente em 17 das últimas 20 sessões; faltou na 1ª, na 2ª e na 3ª",
            format.resumoPresenca(List(20) { it > 2 }),
        )
    }

    @Test
    fun `lista falada e rotulo valor`() {
        assertEquals("", format.listaFalada(emptyList()))
        assertEquals("a", format.listaFalada(listOf("a")))
        assertEquals("a e b", format.listaFalada(listOf("a", "b")))
        assertEquals("a, b e c", format.listaFalada(listOf("a", "b", "c")))
        assertEquals("Cota parlamentar em 2027: 112 mil reais", format.rotuloValor("Cota parlamentar em 2027", "112 mil reais"))
    }
}
