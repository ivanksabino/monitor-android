package dev.monitorcidadao.core.designsystem.a11y

import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertEquals

/**
 * O nó que o TalkBack e o uiautomator/Trail recebem (`AccessibilityNodeInfo`) para o elemento com
 * [tag]. A árvore de semântica do Compose não mostra os nós "falsos" que o Compose cria para nome e
 * papel quando o nó tem filhos semânticos; aqui eles aparecem como filhos (BUG-006).
 */
fun ComposeContentTestRule.noDeAcessibilidade(tag: String): AccessibilityNodeInfo {
    val id = onNodeWithTag(tag).fetchSemanticsNode().id
    val activity = (this as AndroidComposeTestRule<*, *>).activity
    val view = composeView(activity.window.decorView) ?: error("AndroidComposeView não encontrada")
    return view.accessibilityNodeProvider.createAccessibilityNodeInfo(id) ?: error("sem nó para $tag")
}

/** id, nome, papel e estado no MESMO nó, sem filhos que o leitor ou a automação vejam à parte. */
fun AccessibilityNodeInfo.assertNoUnico(nome: String, classe: String) {
    assertEquals("nome no nó do testTag", nome, contentDescription?.toString() ?: text?.toString())
    assertEquals("papel no nó do testTag", classe, className?.toString())
    assertEquals("nó do testTag sem filhos (nome/papel em nós falsos)", 0, childCount)
}

private fun composeView(v: View): View? {
    if (v is ViewRootForTest) return v
    if (v is ViewGroup) for (i in 0 until v.childCount) composeView(v.getChildAt(i))?.let { return it }
    return null
}
