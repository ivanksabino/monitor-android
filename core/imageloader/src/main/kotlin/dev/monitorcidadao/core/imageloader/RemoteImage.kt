package dev.monitorcidadao.core.imageloader

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okhttp3.Call

/**
 * Imagem remota do app. O Coil 3 é exceção consciente (arquitetura §6) e fica escondido aqui:
 * o resto do app só conhece `RemoteImage`.
 *
 * @param contentDescription `null` para imagem decorativa (sai da árvore de acessibilidade).
 */
@Composable
fun RemoteImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: Painter? = null,
) {
    AsyncImage(
        model = url,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        placeholder = placeholder,
        error = placeholder,
    )
}

object RemoteImageLoader {
    /**
     * Instala o carregador global. [callFactory] deve ser o cliente de imagens do `HTTPClient`
     * (`imageCallFactory()`): mesmo pool e segurança, sem os interceptors da API.
     */
    fun install(context: Context, callFactory: () -> Call.Factory) {
        SingletonImageLoader.setSafe { create(context, callFactory) }
    }

    fun create(context: Context, callFactory: () -> Call.Factory): ImageLoader =
        ImageLoader.Builder(context.applicationContext)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = callFactory)) }
            .build()
}
