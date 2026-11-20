package dev.monitorcidadao.feature.entenda

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.monitorcidadao.core.designsystem.component.PlaceholderScreen
import dev.monitorcidadao.core.routing.Router

/** O que a feature Entenda precisa que o app monte (arquitetura §4). */
interface EntendaFactory {
    @Composable
    fun makeEntenda(router: Router)
}

/** Aba Entenda (explicações sobre o Congresso). Placeholder desta entrega: título (h1) + "Em construção". */
@Composable
fun EntendaScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.entenda_titulo),
        message = stringResource(R.string.entenda_em_construcao),
        titleTag = "entenda_titulo",
        modifier = modifier,
    )
}
