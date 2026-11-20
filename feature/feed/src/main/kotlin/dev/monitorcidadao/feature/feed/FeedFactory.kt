package dev.monitorcidadao.feature.feed

import androidx.compose.runtime.Composable
import dev.monitorcidadao.core.routing.Router

/** O que a feature Feed precisa que o app monte (arquitetura §4). */
interface FeedFactory {
    @Composable
    fun makeFeed(router: Router)
}
