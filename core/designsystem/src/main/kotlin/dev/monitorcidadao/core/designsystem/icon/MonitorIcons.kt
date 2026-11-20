package dev.monitorcidadao.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Ícones de traço do protótipo (24×24, traço 2, pontas arredondadas), sem depender do
 * material-icons-extended. A cor vem do `tint` do `Icon`. Sempre decorativos ou com label no controle.
 */
object MonitorIcons {
    val Voltar: ImageVector by lazy { stroke("voltar", "M19 12H5", "M12 19l-7-7 7-7") }
    val Compartilhar: ImageVector by lazy {
        stroke("compartilhar", "M4 12v8a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-8", "M16 6l-4-4-4 4", "M12 2v13")
    }
    val Sino: ImageVector by lazy {
        stroke("sino", "M6 8a6 6 0 1 1 12 0c0 7 3 9 3 9H3s3-2 3-9", "M10.3 21a1.94 1.94 0 0 0 3.4 0")
    }
    val ChevronDireita: ImageVector by lazy { stroke("chevron_direita", "M9 6l6 6-6 6") }
    val Inicio: ImageVector by lazy { stroke("inicio", "M3 10.5L12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z") }
    val Explorar: ImageVector by lazy { stroke("explorar", "M4 11a7 7 0 1 0 14 0a7 7 0 1 0-14 0", "M20 20l-3.5-3.5") }
    val Entenda: ImageVector by lazy {
        stroke("entenda", "M2 5h7a3 3 0 0 1 3 3v12a2 2 0 0 0-2-2H2z", "M22 5h-7a3 3 0 0 0-3 3v12a2 2 0 0 1 2-2h8z")
    }
    val Participe: ImageVector by lazy {
        stroke("participe", "M6 3h12a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z", "M8.5 12l2.5 2.5L16 9.5")
    }
    val Voce: ImageVector by lazy { stroke("voce", "M8 8a4 4 0 1 0 8 0a4 4 0 1 0-8 0", "M4 21a8 8 0 0 1 16 0") }

    private fun stroke(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            paths.forEach { data ->
                addPath(
                    pathData = addPathNodes(data),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()
}
