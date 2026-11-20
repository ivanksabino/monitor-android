package dev.monitorcidadao.data.api.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** Bloco de conteúdo do post, discriminado por `tipo`. */
@Serializable(with = BlocoDtoSerializer::class)
sealed interface BlocoDto {
    val tipo: String

    @Serializable
    data class Placar(
        @SerialName("sim") val sim: Int,
        @SerialName("nao") val nao: Int,
        @SerialName("abstencao") val abstencao: Int = 0,
    ) : BlocoDto {
        override val tipo: String get() = TIPO_PLACAR
    }

    @Serializable
    data class ValorDestaque(
        @SerialName("centavos") val centavos: Long,
        @SerialName("rotulo") val rotulo: String = "",
    ) : BlocoDto {
        override val tipo: String get() = TIPO_VALOR_DESTAQUE
    }

    @Serializable
    data class Fonte(
        @SerialName("texto") val texto: String,
        @SerialName("url") val url: String? = null,
    ) : BlocoDto {
        override val tipo: String get() = TIPO_FONTE
    }

    /** Bloco que esta versão não conhece (ou conhecido mas malformado). O mapeamento descarta. */
    data class Desconhecido(override val tipo: String) : BlocoDto

    companion object {
        const val TIPO_PLACAR = "placar"
        const val TIPO_VALOR_DESTAQUE = "valor_destaque"
        const val TIPO_FONTE = "fonte"
    }
}

/**
 * Lê o objeto inteiro, escolhe o serializer pelo `tipo` e, se o tipo for novo ou o corpo não
 * bater, devolve [BlocoDto.Desconhecido] em vez de derrubar a página toda.
 */
internal object BlocoDtoSerializer : KSerializer<BlocoDto> {
    override val descriptor: SerialDescriptor = JsonObject.serializer().descriptor

    override fun deserialize(decoder: Decoder): BlocoDto {
        val input = decoder as? JsonDecoder ?: throw SerializationException("BlocoDto só é lido de JSON")
        val objeto = input.decodeJsonElement() as? JsonObject ?: return BlocoDto.Desconhecido("")
        val tipo = (objeto["tipo"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val serializer: KSerializer<out BlocoDto> = when (tipo) {
            BlocoDto.TIPO_PLACAR -> BlocoDto.Placar.serializer()
            BlocoDto.TIPO_VALOR_DESTAQUE -> BlocoDto.ValorDestaque.serializer()
            BlocoDto.TIPO_FONTE -> BlocoDto.Fonte.serializer()
            else -> return BlocoDto.Desconhecido(tipo)
        }
        return try {
            input.json.decodeFromJsonElement(serializer, objeto)
        } catch (_: SerializationException) {
            BlocoDto.Desconhecido(tipo)
        } catch (_: IllegalArgumentException) {
            BlocoDto.Desconhecido(tipo)
        }
    }

    override fun serialize(encoder: Encoder, value: BlocoDto) {
        val output = encoder as? JsonEncoder ?: throw SerializationException("BlocoDto só é escrito em JSON")
        val corpo = when (value) {
            is BlocoDto.Placar -> output.json.encodeToJsonElement(BlocoDto.Placar.serializer(), value).jsonObject
            is BlocoDto.ValorDestaque -> output.json.encodeToJsonElement(BlocoDto.ValorDestaque.serializer(), value).jsonObject
            is BlocoDto.Fonte -> output.json.encodeToJsonElement(BlocoDto.Fonte.serializer(), value).jsonObject
            is BlocoDto.Desconhecido -> JsonObject(emptyMap())
        }
        output.encodeJsonElement(JsonObject(mapOf("tipo" to JsonPrimitive(value.tipo)) + corpo))
    }
}
