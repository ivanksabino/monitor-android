package dev.monitorcidadao.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// DTOs do contrato v1 (contracts/api.md), nomes de campo exatamente como no JSON.
// Dinheiro chega em centavos (inteiro) e datas em ISO 8601 (texto): a conversão para o
// domínio fica no Data (o APIClient só depende do Network, arquitetura §2).

@Serializable
data class PessoaResumoDto(
    @SerialName("id") val id: String,
    @SerialName("nome") val nome: String,
    @SerialName("cargo") val cargo: String,
    @SerialName("partido") val partido: String,
    @SerialName("uf") val uf: String,
    @SerialName("iniciais") val iniciais: String,
)

/** `GET /v1/seguindo`. */
@Serializable
data class SeguindoDto(@SerialName("itens") val itens: List<PessoaResumoDto> = emptyList())

@Serializable
data class MandatoDto(
    @SerialName("inicio") val inicio: Int,
    @SerialName("fim") val fim: Int,
    @SerialName("numero") val numero: Int,
)

@Serializable
data class ResumoDto(
    @SerialName("ano") val ano: Int,
    @SerialName("presenca_pct") val presencaPct: Int,
    @SerialName("projetos_apresentados") val projetosApresentados: Int,
    @SerialName("cota_centavos") val cotaCentavos: Long,
    @SerialName("emendas_indicadas_centavos") val emendasIndicadasCentavos: Long,
)

@Serializable
data class PatrimonioDto(
    @SerialName("ano") val ano: Int,
    @SerialName("centavos") val centavos: Long,
)

@Serializable
data class NumerosDto(
    @SerialName("presenca_ultimas") val presencaUltimas: List<Boolean> = emptyList(),
    @SerialName("cota_centavos") val cotaCentavos: Long,
    @SerialName("emendas_pagas_centavos") val emendasPagasCentavos: Long,
    @SerialName("emendas_indicadas_centavos") val emendasIndicadasCentavos: Long,
    @SerialName("patrimonio_tse") val patrimonioTse: PatrimonioDto,
    @SerialName("votou_com_partido_pct") val votouComPartidoPct: Int,
    @SerialName("fontes") val fontes: List<String> = emptyList(),
)

/** `GET /v1/pessoas/{id}`. */
@Serializable
data class PessoaDto(
    @SerialName("id") val id: String,
    @SerialName("nome") val nome: String,
    @SerialName("cargo") val cargo: String,
    @SerialName("partido") val partido: String,
    @SerialName("uf") val uf: String,
    @SerialName("iniciais") val iniciais: String,
    @SerialName("em_exercicio") val emExercicio: Boolean,
    @SerialName("mandato") val mandato: MandatoDto,
    @SerialName("resumo") val resumo: ResumoDto,
    @SerialName("numeros") val numeros: NumerosDto,
    @SerialName("atualizado_em") val atualizadoEm: String,
)

/**
 * Post polimórfico: `tipo` fica como texto (tipo desconhecido é tratado no mapeamento, vira card
 * genérico); `blocos` tolera bloco desconhecido ou malformado ([BlocoDto.Desconhecido]).
 */
@Serializable
data class PostDto(
    @SerialName("id") val id: String,
    @SerialName("tipo") val tipo: String,
    @SerialName("pessoa_id") val pessoaId: String,
    @SerialName("publicado_em") val publicadoEm: String,
    @SerialName("titulo") val titulo: String = "",
    @SerialName("corpo") val corpo: String = "",
    @SerialName("blocos") val blocos: List<BlocoDto> = emptyList(),
    @SerialName("tem_explicacao") val temExplicacao: Boolean = false,
)

/** `GET /v1/pessoas/{id}/posts?cursor=`. `proximo_cursor: null` = fim. */
@Serializable
data class PaginaPostsDto(
    @SerialName("itens") val itens: List<PostDto> = emptyList(),
    @SerialName("proximo_cursor") val proximoCursor: String? = null,
)
