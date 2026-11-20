package dev.monitorcidadao.core.analytics

import dev.monitorcidadao.core.testing.RecordingAnalyticsSink
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AnalyticsTest {
    @Test
    fun `fan-out entrega a todos mesmo se um sink falhar`() {
        val recorder = RecordingAnalyticsSink()
        val analytics = Analytics(listOf(AnalyticsSink { error("sdk quebrado") }, recorder))

        analytics.track(AnalyticsEvent.SeguiuPessoa("cam-1001"))

        assertEquals(listOf(AnalyticsEvent.SeguiuPessoa("cam-1001")), recorder.events)
    }

    @Test
    fun `log sink formata nome e parametros ordenados`() {
        val lines = mutableListOf<String>()
        LogAnalyticsSink(lines::add).track(AnalyticsEvent.PerfilAberto("cam-1001", Tela.Inicio))
        assertEquals(listOf("[analytics] perfil_aberto {origem=inicio, pessoa_id=cam-1001}"), lines)
    }

    @Test
    fun `nomes de evento sao unicos no catalogo`() {
        val names = listOf(
            AnalyticsEvent.TelaVista(Tela.Perfil),
            AnalyticsEvent.PerfilAberto("x", Tela.Inicio),
            AnalyticsEvent.SeguiuPessoa("x"),
            AnalyticsEvent.DeixouDeSeguir("x"),
            AnalyticsEvent.SegmentoTrocado(Tela.Perfil, "resumo"),
            AnalyticsEvent.ExplicacaoAberta("x"),
            AnalyticsEvent.PostCompartilhado("x"),
            AnalyticsEvent.ErroExibido(Tela.Perfil, "x"),
        ).map { it.name }
        assertEquals(names.size, names.toSet().size)
    }
}
