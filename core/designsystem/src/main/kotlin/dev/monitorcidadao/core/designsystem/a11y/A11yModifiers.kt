package dev.monitorcidadao.core.designsystem.a11y

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role

/**
 * Uma parada por unidade de sentido (a11y §3): o contêiner vira UM elemento com o texto humano
 * [label] e os filhos saem da árvore. Nunca use em contêiner com mais de um controle interativo.
 */
fun Modifier.a11yElement(label: String, role: Role? = null): Modifier = clearAndSetSemantics {
    contentDescription = label
    role?.let { this.role = it }
}

/** Título com nome acessível reescrito (ex.: título do post com tipo e data por extenso). */
fun Modifier.a11yHeading(label: String): Modifier = clearAndSetSemantics {
    contentDescription = label
    heading()
}

/** Decorativo sai da árvore inteira (a11y §8). Não use em contêiner com controle real. */
fun Modifier.decorative(): Modifier = clearAndSetSemantics { }
