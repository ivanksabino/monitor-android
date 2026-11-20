package dev.monitorcidadao.core.routing

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.navigation3.runtime.NavKey

/** Pilha de uma aba. O `NavDisplay` do app observa [backStack]; ViewModels só chamam os verbos. */
@Stable
class Router(val start: Route) {
    val backStack: SnapshotStateList<NavKey> = mutableStateListOf(start)

    var sheet: Sheet? by mutableStateOf(null)
        private set

    val current: Route get() = backStack.last() as Route

    /**
     * Empilha [route]. Toque duplo rápido não empilha a mesma rota duas vezes: cada `contentKey`
     * é único na pilha do Navigation 3 (senão as duas entradas dividem ViewModel e estado salvo).
     */
    fun push(route: Route) {
        if (backStack.lastOrNull() == route) return
        backStack.add(route)
    }

    fun pop() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    fun popToRoot() {
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    /** Substitui tudo acima da raiz (deep link). */
    fun reset(routes: List<Route>) {
        popToRoot()
        routes.filterNot { it == start }.forEach(backStack::add)
    }

    fun present(sheet: Sheet) {
        this.sheet = sheet
    }

    fun dismissSheet() {
        sheet = null
    }
}
