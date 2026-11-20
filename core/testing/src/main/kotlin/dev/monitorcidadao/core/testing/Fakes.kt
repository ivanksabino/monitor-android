package dev.monitorcidadao.core.testing

import dev.monitorcidadao.core.analytics.AnalyticsEvent
import dev.monitorcidadao.core.analytics.AnalyticsSink
import java.util.concurrent.CopyOnWriteArrayList

/** Guarda os eventos para o teste conferir. */
class RecordingAnalyticsSink : AnalyticsSink {
    private val recorded = CopyOnWriteArrayList<AnalyticsEvent>()
    val events: List<AnalyticsEvent> get() = recorded.toList()

    override fun track(event: AnalyticsEvent) {
        recorded += event
    }
}
