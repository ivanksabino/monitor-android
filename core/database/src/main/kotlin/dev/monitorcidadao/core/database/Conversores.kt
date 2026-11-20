package dev.monitorcidadao.core.database

import androidx.room.TypeConverter
import dev.monitorcidadao.core.database.entity.BlocoColuna
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** TypeConverters das colunas compostas. Leitura tolerante: JSON inválido vira lista vazia. */
internal class Conversores {
    @TypeConverter
    fun blocosParaTexto(blocos: List<BlocoColuna>): String = json.encodeToString(blocosSerializer, blocos)

    @TypeConverter
    fun textoParaBlocos(texto: String): List<BlocoColuna> = lerOuVazio { json.decodeFromString(blocosSerializer, texto) }

    @TypeConverter
    fun textosParaTexto(lista: List<String>): String = json.encodeToString(textosSerializer, lista)

    @TypeConverter
    fun textoParaTextos(texto: String): List<String> = lerOuVazio { json.decodeFromString(textosSerializer, texto) }

    /** Presenças como "1101…" (compacto e legível num dump do banco). */
    @TypeConverter
    fun presencasParaTexto(lista: List<Boolean>): String = lista.joinToString("") { if (it) "1" else "0" }

    @TypeConverter
    fun textoParaPresencas(texto: String): List<Boolean> = texto.map { it == '1' }

    private inline fun <T> lerOuVazio(block: () -> List<T>): List<T> = try {
        block()
    } catch (_: SerializationException) {
        emptyList()
    } catch (_: IllegalArgumentException) {
        emptyList()
    }
}

/**
 * Fora da classe dos conversores de propósito: o KSP do Room lê os membros de [Conversores] antes
 * do plugin de serialização gerar `serializer()`, e um tipo inferido daqui não resolveria.
 */
private val json: Json = Json {
    ignoreUnknownKeys = true
    classDiscriminator = "tipo"
}
private val blocosSerializer: KSerializer<List<BlocoColuna>> = ListSerializer(BlocoColuna.serializer())
private val textosSerializer: KSerializer<List<String>> = ListSerializer(String.serializer())
