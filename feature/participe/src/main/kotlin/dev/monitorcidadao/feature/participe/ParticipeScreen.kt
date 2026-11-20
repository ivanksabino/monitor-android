package dev.monitorcidadao.feature.participe

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.monitorcidadao.core.designsystem.component.PlaceholderScreen
import dev.monitorcidadao.core.routing.Router

/** O que a feature Participe precisa que o app monte (arquitetura §4). */
interface ParticipeFactory {
    @Composable
    fun makeParticipe(router: Router)
}

/** Aba Participe (formas de participação). Placeholder desta entrega: título (h1) + "Em construção". */
@Composable
fun ParticipeScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.participe_titulo),
        message = stringResource(R.string.participe_em_construcao),
        titleTag = "participe_titulo",
        modifier = modifier,
    )
}
