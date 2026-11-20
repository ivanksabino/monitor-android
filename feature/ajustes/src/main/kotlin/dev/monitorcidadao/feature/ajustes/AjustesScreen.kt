package dev.monitorcidadao.feature.ajustes

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.monitorcidadao.core.designsystem.component.PlaceholderScreen
import dev.monitorcidadao.core.routing.Router

/** O que a feature Ajustes precisa que o app monte (arquitetura §4). */
interface AjustesFactory {
    @Composable
    fun makeAjustes(router: Router)
}

/** Aba Você (ajustes e preferências). Placeholder desta entrega: título (h1) + "Em construção". */
@Composable
fun AjustesScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.ajustes_titulo),
        message = stringResource(R.string.ajustes_em_construcao),
        titleTag = "voce_titulo",
        modifier = modifier,
    )
}
