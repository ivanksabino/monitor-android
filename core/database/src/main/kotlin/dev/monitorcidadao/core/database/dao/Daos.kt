package dev.monitorcidadao.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import dev.monitorcidadao.core.database.entity.ControleEntity
import dev.monitorcidadao.core.database.entity.FeedPaginaEntity
import dev.monitorcidadao.core.database.entity.FilaEnvioEntity
import dev.monitorcidadao.core.database.entity.PessoaEntity
import dev.monitorcidadao.core.database.entity.PostEntity
import dev.monitorcidadao.core.database.entity.SeguidoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PessoaDao {
    /** Emite de novo a cada gravação na tabela (observação reativa). */
    @Query("SELECT * FROM pessoa WHERE id = :id")
    fun observar(id: String): Flow<PessoaEntity?>

    @Upsert
    suspend fun salvar(pessoa: PessoaEntity)
}

/** Um feed salvo, já remontado na ordem das páginas. */
data class FeedSalvo(
    val posts: List<PostEntity>,
    /** Cursor para continuar depois da última página salva (`null` = o feed acabou). */
    val proximoCursor: String?,
    val paginas: Int,
)

/** Posts + páginas do feed: gravação e leitura sempre juntas, numa transação. */
@Dao
abstract class FeedDao {
    @Upsert
    protected abstract suspend fun salvarPosts(posts: List<PostEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun inserirPagina(pagina: FeedPaginaEntity)

    @Query("DELETE FROM feed_pagina WHERE feed = :feed")
    abstract suspend fun limpar(feed: String)

    @Query("SELECT * FROM feed_pagina WHERE feed = :feed ORDER BY ordem")
    abstract suspend fun paginas(feed: String): List<FeedPaginaEntity>

    @Query("SELECT * FROM feed_pagina WHERE feed = :feed ORDER BY ordem")
    abstract fun observarPaginas(feed: String): Flow<List<FeedPaginaEntity>>

    @Query("SELECT * FROM post WHERE id IN (:ids)")
    protected abstract suspend fun postsPorId(ids: List<String>): List<PostEntity>

    @Query("SELECT * FROM post WHERE id = :id")
    abstract suspend fun post(id: String): PostEntity?

    @Query("SELECT * FROM feed_pagina WHERE feed = :feed ORDER BY ordem DESC LIMIT 1")
    protected abstract suspend fun ultimaPagina(feed: String): FeedPaginaEntity?

    /**
     * Grava uma página. A primeira ([cursor] `null`) substitui o feed inteiro (as seguintes,
     * com cursores antigos, deixam de valer). As demais só entram se continuarem a cadeia: o
     * [cursor] tem de ser o `proximo_cursor` da última página salva. Senão (a primeira página nova
     * não foi gravada, ou dois paginadores do mesmo feed se intercalaram) a página é descartada e
     * o feed salvo continua coerente.
     * @return `true` se a página entrou no banco.
     */
    @Transaction
    open suspend fun salvarPagina(feed: String, cursor: String?, proximoCursor: String?, posts: List<PostEntity>, agora: Long): Boolean {
        val ordem = if (cursor == null) {
            limpar(feed)
            0
        } else {
            val ultima = ultimaPagina(feed)
            if (ultima == null || ultima.proximoCursor != cursor) return false
            ultima.ordem + 1
        }
        salvarPosts(posts)
        inserirPagina(FeedPaginaEntity(feed, ordem, cursor, proximoCursor, posts.map { it.id }, agora))
        return true
    }

    /** `null` quando o feed nunca foi salvo. Post repetido entre páginas aparece uma vez só. */
    @Transaction
    open suspend fun ler(feed: String): FeedSalvo? {
        val paginas = paginas(feed)
        if (paginas.isEmpty()) return null
        val ids = paginas.flatMap { it.postIds }.distinct()
        val porId = ids.chunked(MAX_PARAMETROS).flatMap { postsPorId(it) }.associateBy { it.id }
        return FeedSalvo(
            posts = ids.mapNotNull(porId::get),
            proximoCursor = paginas.last().proximoCursor,
            paginas = paginas.size,
        )
    }

    private companion object {
        /** Limite seguro de parâmetros do SQLite antigo (API 26). */
        const val MAX_PARAMETROS = 900
    }
}

@Dao
abstract class SeguidoDao {
    @Query("SELECT * FROM seguido WHERE seguindo = 1 ORDER BY nome COLLATE NOCASE")
    abstract fun observarSeguidos(): Flow<List<SeguidoEntity>>

    @Query("SELECT seguindo FROM seguido WHERE pessoa_id = :pessoaId")
    abstract fun observarSeguindo(pessoaId: String): Flow<Boolean?>

    @Upsert
    abstract suspend fun salvar(seguido: SeguidoEntity)

    @Query("UPDATE seguido SET seguindo = :seguindo, atualizado_em = :agora WHERE pessoa_id = :pessoaId")
    abstract suspend fun definirSeguindo(pessoaId: String, seguindo: Boolean, agora: Long)

    @Query("SELECT COUNT(*) FROM seguido")
    abstract suspend fun contar(): Int

    /** Só entra quem ainda não está no banco: a linha local (seguir/deixar de seguir) sempre vence. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun inserirNovos(seguidos: List<SeguidoEntity>)

    @Query("SELECT valor FROM controle WHERE chave = :chave")
    protected abstract suspend fun lerControle(chave: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun gravarControle(controle: ControleEntity)

    /** A lista do servidor já foi trazida uma vez (depois disso, o aparelho manda). */
    open suspend fun importado(): Boolean = lerControle(CHAVE_IMPORTADO) == "1"

    /**
     * Mescla a lista do servidor com o que já está no aparelho e marca a importação como feita,
     * numa transação só (ou entra tudo, ou nada e a próxima abertura tenta de novo).
     */
    @Transaction
    open suspend fun importar(seguidos: List<SeguidoEntity>) {
        inserirNovos(seguidos)
        gravarControle(ControleEntity(CHAVE_IMPORTADO, "1"))
    }

    private companion object {
        const val CHAVE_IMPORTADO = "seguindo_importado"
    }
}

/**
 * Fila de reenvio: um pedido por pessoa (índice único em `pessoa_id`). As operações que leem e
 * escrevem o mesmo pedido rodam numa transação.
 */
@Dao
abstract class FilaEnvioDao {
    @Query("SELECT * FROM fila_envio ORDER BY id")
    abstract suspend fun pendentes(): List<FilaEnvioEntity>

    @Query("SELECT * FROM fila_envio WHERE pessoa_id = :pessoaId")
    abstract suspend fun pendente(pessoaId: String): FilaEnvioEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun inserir(envio: FilaEnvioEntity)

    @Query("DELETE FROM fila_envio WHERE pessoa_id = :pessoaId")
    protected abstract suspend fun apagar(pessoaId: String)

    @Query("UPDATE fila_envio SET estado_no_servidor = :estado WHERE pessoa_id = :pessoaId")
    protected abstract suspend fun definirEstadoNoServidor(pessoaId: String, estado: Boolean)

    /**
     * Põe o pedido na fila. Se já havia um da mesma pessoa, o novo o substitui (vai para o fim da
     * fila) mas herda o `estado_no_servidor` dele: o servidor ainda não confirmou nenhum dos dois.
     */
    @Transaction
    open suspend fun enfileirar(pessoaId: String, seguir: Boolean, estadoNoServidor: Boolean, agora: Long) {
        val anterior = pendente(pessoaId)
        inserir(
            FilaEnvioEntity(
                pessoaId = pessoaId,
                seguir = seguir,
                estadoNoServidor = anterior?.estadoNoServidor ?: estadoNoServidor,
                criadoEm = agora,
            ),
        )
    }

    /**
     * O servidor recebeu o pedido. Se ele ainda é o guardado, sai da fila; se um mais novo o
     * substituiu, o servidor agora está em [seguir] e é isso que o mais novo passa a carregar.
     */
    @Transaction
    open suspend fun confirmar(pessoaId: String, seguir: Boolean) {
        val atual = pendente(pessoaId) ?: return
        if (atual.seguir == seguir) apagar(pessoaId) else definirEstadoNoServidor(pessoaId, seguir)
    }

    /**
     * Falha definitiva. Se o pedido ainda é o guardado, sai da fila e devolve o estado que o
     * servidor tem (para onde reverter); `null` se um pedido mais novo o substituiu.
     */
    @Transaction
    open suspend fun descartar(pessoaId: String, seguir: Boolean): Boolean? {
        val atual = pendente(pessoaId)?.takeIf { it.seguir == seguir } ?: return null
        apagar(pessoaId)
        return atual.estadoNoServidor
    }
}
