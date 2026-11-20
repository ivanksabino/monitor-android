package dev.monitorcidadao.core.routing

import dev.monitorcidadao.domain.PessoaId
import dev.monitorcidadao.domain.PostId
import java.net.URI

/** Deep link resolvido: em qual aba abrir e o que empilhar acima da raiz dela. */
data class DeepLink(val tab: Tab, val stack: List<Route>)

/**
 * Mesmos links do iOS:
 * - `monitorcidadao://perfil/{id}` → aba Início com `[Route.Perfil(id)]`;
 * - `monitorcidadao://post/{id}` → aba Início com `[Route.Post(id)]`.
 */
object DeepLinkParser {
    const val SCHEME = "monitorcidadao"

    fun parse(uri: String): DeepLink? {
        val parsed = runCatching { URI(uri) }.getOrNull() ?: return null
        if (!parsed.scheme.equals(SCHEME, ignoreCase = true)) return null
        val segments = listOfNotNull(parsed.host) +
            parsed.path.orEmpty().split('/').filter { it.isNotBlank() }
        if (segments.size != 2) return null
        val id = segments[1]
        return when (segments[0]) {
            "perfil" -> DeepLink(Tab.Inicio, listOf(Route.Perfil(PessoaId(id))))
            "post" -> DeepLink(Tab.Inicio, listOf(Route.Post(PostId(id))))
            else -> null
        }
    }
}
