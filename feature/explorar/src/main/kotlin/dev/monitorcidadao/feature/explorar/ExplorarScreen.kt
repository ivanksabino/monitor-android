package dev.monitorcidadao.feature.explorar

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.monitorcidadao.core.designsystem.component.PlaceholderScreen
import dev.monitorcidadao.core.routing.Router

/** O que a feature Explorar precisa que o app monte (arquitetura §4). */
interface ExplorarFactory {
    @Composable
    fun makeExplorar(router: Router)
}

/** Aba Explorar (busca de pessoas e temas). Placeholder desta entrega: título (h1) + "Em construção". */
@Composable
fun ExplorarScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.explorar_titulo),
        message = stringResource(R.string.explorar_em_construcao),
        titleTag = "explorar_titulo",
        modifier = modifier,
    )
}
