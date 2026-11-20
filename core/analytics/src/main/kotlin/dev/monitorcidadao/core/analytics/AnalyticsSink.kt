package dev.monitorcidadao.core.analytics

/** Destino de eventos. O app injeta o SDK real atrás desta interface. */
fun interface AnalyticsSink {
    fun track(event: AnalyticsEvent)
}

/** Fan-out para vários sinks; falha de um sink nunca derruba o app nem os outros. */
class Analytics(private val sinks: List<AnalyticsSink>) : AnalyticsSink {
    override fun track(event: AnalyticsEvent) {
        sinks.forEach { sink -> runCatching { sink.track(event) } }
    }
}

/** Sink de desenvolvimento: escreve cada evento numa linha. */
class LogAnalyticsSink(private val log: (String) -> Unit) : AnalyticsSink {
    override fun track(event: AnalyticsEvent) {
        val params = event.params.entries.sortedBy { it.key }.joinToString(", ") { "${it.key}=${it.value}" }
        log("[analytics] ${event.name} {$params}")
    }
}
