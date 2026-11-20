package dev.monitorcidadao.core.imageloader

import coil3.request.CachePolicy
import coil3.request.ImageRequest
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
class RemoteImageLoaderTest {
    private val urls = mutableListOf<String>()
    private val chamadas = AtomicInteger()

    /** Cliente "do app": responde sem rede e registra o que passou por ele. */
    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            chamadas.incrementAndGet()
            synchronized(urls) { urls += chain.request().url.toString() }
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(PNG_1X1.toResponseBody("image/png".toMediaType()))
                .build()
        }
        .build()

    @Test
    fun `imagens saem pelo Call Factory injetado, nao por um cliente proprio do Coil`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val loader = RemoteImageLoader.create(context) { client }

        // Sem cache de disco/memória: senão uma execução anterior serve a imagem e nada passa pelo cliente.
        loader.execute(
            ImageRequest.Builder(context)
                .data("https://img.monitorcidadao.invalid/avatar/cam-1001.png")
                .diskCachePolicy(CachePolicy.DISABLED)
                .memoryCachePolicy(CachePolicy.DISABLED)
                .build(),
        )

        assertEquals(1, chamadas.get())
        assertEquals(listOf("https://img.monitorcidadao.invalid/avatar/cam-1001.png"), urls)
    }

    private companion object {
        val PNG_1X1: ByteArray = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==",
        )
    }
}
