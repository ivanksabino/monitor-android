package dev.monitorcidadao.data.repository.mapping

import dev.monitorcidadao.data.api.dto.BlocoDto
import dev.monitorcidadao.data.api.dto.PaginaPostsDto
import dev.monitorcidadao.data.api.dto.PessoaDto
import dev.monitorcidadao.data.api.dto.PessoaResumoDto
import dev.monitorcidadao.data.api.dto.PostDto
import dev.monitorcidadao.domain.AppError
import dev.monitorcidadao.domain.AppException
import dev.monitorcidadao.domain.Cargo
import dev.monitorcidadao.domain.Dinheiro
import dev.monitorcidadao.domain.Mandato
import dev.monitorcidadao.domain.NumerosMandato
import dev.monitorcidadao.domain.PaginaPosts
import dev.monitorcidadao.domain.Patrimonio
import dev.monitorcidadao.domain.Pessoa
import dev.monitorcidadao.domain.PessoaId
import dev.monitorcidadao.domain.PessoaResumo
import dev.monitorcidadao.domain.Post
import dev.monitorcidadao.domain.PostBloco
import dev.monitorcidadao.domain.PostId
import dev.monitorcidadao.domain.PostTipo
import dev.monitorcidadao.domain.ResumoMandato
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

// DTO da API → entidade de domínio. Tolerância do contrato:
// - tipo de post desconhecido → PostTipo.Desconhecido (card genérico);
// - bloco desconhecido ou malformado → descartado;
// - post com id vazio ou data inválida → descartado (a página continua);
// - perfil com cargo desconhecido ou data inválida → DadosInvalidos (não há tela sem esses campos).

/** @throws AppException com [AppError.DadosInvalidos] quando o perfil não fecha com o contrato. */
fun PessoaDto.paraDominio(): Pessoa = Pessoa(
    id = PessoaId(id),
    nome = nome,
    cargo = cargoOuFalha(cargo),
    partido = partido,
    uf = uf,
    iniciais = iniciais,
    emExercicio = emExercicio,
    mandato = Mandato(mandato.inicio, mandato.fim, mandato.numero),
    resumo = ResumoMandato(
        ano = resumo.ano,
        presencaPct = resumo.presencaPct,
        projetosApresentados = resumo.projetosApresentados,
        cota = Dinheiro(resumo.cotaCentavos),
        emendasIndicadas = Dinheiro(resumo.emendasIndicadasCentavos),
    ),
    numeros = NumerosMandato(
        presencaUltimas = numeros.presencaUltimas,
        cota = Dinheiro(numeros.cotaCentavos),
        emendasPagas = Dinheiro(numeros.emendasPagasCentavos),
        emendasIndicadas = Dinheiro(numeros.emendasIndicadasCentavos),
        patrimonioTse = Patrimonio(numeros.patrimonioTse.ano, Dinheiro(numeros.patrimonioTse.centavos)),
        votouComPartidoPct = numeros.votouComPartidoPct,
        fontes = numeros.fontes,
    ),
    atualizadoEm = dataIso(atualizadoEm) ?: throw AppException(AppError.DadosInvalidos),
)

/** `null` quando o cargo é desconhecido (o item é pulado na lista). */
fun PessoaResumoDto.paraDominioOuNull(): PessoaResumo? =
    Cargo.from(cargo)?.let { PessoaResumo(PessoaId(id), nome, it, partido, uf, iniciais) }

fun PaginaPostsDto.paraDominio(): PaginaPosts =
    PaginaPosts(itens = itens.mapNotNull { it.paraDominioOuNull() }, proximoCursor = proximoCursor)

fun PostDto.paraDominioOuNull(): Post? {
    if (id.isBlank()) return null
    val data = dataIso(publicadoEm) ?: return null
    return Post(
        id = PostId(id),
        tipo = PostTipo.from(tipo),
        pessoaId = PessoaId(pessoaId),
        publicadoEm = data,
        titulo = titulo,
        corpo = corpo,
        blocos = blocos.mapNotNull { it.paraDominioOuNull() },
        temExplicacao = temExplicacao,
    )
}

fun BlocoDto.paraDominioOuNull(): PostBloco? = when (this) {
    is BlocoDto.Placar -> PostBloco.Placar(sim, nao, abstencao)
    is BlocoDto.ValorDestaque -> PostBloco.ValorDestaque(Dinheiro(centavos), rotulo)
    is BlocoDto.Fonte -> PostBloco.Fonte(texto, url)
    is BlocoDto.Desconhecido -> null
}

/** ISO 8601 com fuso (`2027-03-12T18:40:00-03:00`). */
internal fun dataIso(texto: String): OffsetDateTime? = try {
    OffsetDateTime.parse(texto)
} catch (_: DateTimeParseException) {
    null
}

private fun cargoOuFalha(valor: String): Cargo = Cargo.from(valor) ?: throw AppException(AppError.DadosInvalidos)
