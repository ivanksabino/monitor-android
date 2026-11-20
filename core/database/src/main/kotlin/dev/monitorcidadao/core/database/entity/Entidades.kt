package dev.monitorcidadao.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Tabelas do cache (arquitetura §6). O Database não conhece o Domain: o Data converte
// entidade de banco ↔ entidade de domínio. Datas do contrato ficam em texto ISO 8601,
// dinheiro em centavos; instantes locais (salvo_em) em epoch millis.

@Entity(tableName = "pessoa")
data class PessoaEntity(
    @PrimaryKey val id: String,
    val nome: String,
    val cargo: String,
    val partido: String,
    val uf: String,
    val iniciais: String,
    @ColumnInfo(name = "em_exercicio") val emExercicio: Boolean,
    @Embedded(prefix = "mandato_") val mandato: MandatoColunas,
    @Embedded(prefix = "resumo_") val resumo: ResumoColunas,
    @Embedded(prefix = "numeros_") val numeros: NumerosColunas,
    @ColumnInfo(name = "atualizado_em") val atualizadoEm: String,
    @ColumnInfo(name = "salvo_em") val salvoEm: Long,
)

data class MandatoColunas(val inicio: Int, val fim: Int, val numero: Int)

data class ResumoColunas(
    val ano: Int,
    @ColumnInfo(name = "presenca_pct") val presencaPct: Int,
    @ColumnInfo(name = "projetos_apresentados") val projetosApresentados: Int,
    @ColumnInfo(name = "cota_centavos") val cotaCentavos: Long,
    @ColumnInfo(name = "emendas_indicadas_centavos") val emendasIndicadasCentavos: Long,
)

data class NumerosColunas(
    @ColumnInfo(name = "presenca_ultimas") val presencaUltimas: List<Boolean>,
    @ColumnInfo(name = "cota_centavos") val cotaCentavos: Long,
    @ColumnInfo(name = "emendas_pagas_centavos") val emendasPagasCentavos: Long,
    @ColumnInfo(name = "emendas_indicadas_centavos") val emendasIndicadasCentavos: Long,
    @ColumnInfo(name = "patrimonio_tse_ano") val patrimonioTseAno: Int,
    @ColumnInfo(name = "patrimonio_tse_centavos") val patrimonioTseCentavos: Long,
    @ColumnInfo(name = "votou_com_partido_pct") val votouComPartidoPct: Int,
    val fontes: List<String>,
)

/** Post polimórfico: `tipo` em texto (inclusive o desconhecido) e `blocos` numa coluna JSON. */
@Entity(tableName = "post", indices = [Index("pessoa_id")])
data class PostEntity(
    @PrimaryKey val id: String,
    val tipo: String,
    @ColumnInfo(name = "pessoa_id") val pessoaId: String,
    @ColumnInfo(name = "publicado_em") val publicadoEm: String,
    val titulo: String,
    val corpo: String,
    val blocos: List<BlocoColuna>,
    @ColumnInfo(name = "tem_explicacao") val temExplicacao: Boolean,
)

/**
 * Uma página de um feed já baixada: o [cursor] que a pediu (`null` = primeira), o
 * [proximoCursor] que ela devolveu e os ids dos posts na ordem do servidor.
 * [feed] identifica a lista (ex.: `pessoa:cam-1001`); [ordem] é a posição da página.
 */
@Entity(tableName = "feed_pagina", primaryKeys = ["feed", "ordem"])
data class FeedPaginaEntity(
    val feed: String,
    val ordem: Int,
    val cursor: String?,
    @ColumnInfo(name = "proximo_cursor") val proximoCursor: String?,
    @ColumnInfo(name = "post_ids") val postIds: List<String>,
    @ColumnInfo(name = "salvo_em") val salvoEm: Long,
)

/**
 * Quem o usuário segue: só no aparelho (LGPD). Deixar de seguir só desliga [seguindo]
 * (o resumo fica para reverter sem depender da rede).
 */
@Entity(tableName = "seguido")
data class SeguidoEntity(
    @PrimaryKey @ColumnInfo(name = "pessoa_id") val pessoaId: String,
    val nome: String,
    val cargo: String,
    val partido: String,
    val uf: String,
    val iniciais: String,
    val seguindo: Boolean,
    @ColumnInfo(name = "atualizado_em") val atualizadoEm: Long,
)

/**
 * Fila de reenvio de seguir/deixar de seguir. Um pedido por pessoa: o mais novo substitui.
 * [estadoNoServidor] é o que o servidor tinha quando o primeiro pedido ainda não enviado entrou
 * (um pedido que substitui outro herda o valor): é para ele que o app volta numa falha definitiva.
 */
@Entity(tableName = "fila_envio", indices = [Index(value = ["pessoa_id"], unique = true)])
data class FilaEnvioEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "pessoa_id") val pessoaId: String,
    val seguir: Boolean,
    @ColumnInfo(name = "estado_no_servidor") val estadoNoServidor: Boolean,
    @ColumnInfo(name = "criado_em") val criadoEm: Long,
)

/** Marcas de controle do app que precisam andar junto com os dados (ex.: lista de seguidos já importada). */
@Entity(tableName = "controle")
data class ControleEntity(
    @PrimaryKey val chave: String,
    val valor: String,
)
