package dev.monitorcidadao.data.repository.mapping

import dev.monitorcidadao.core.database.entity.BlocoColuna
import dev.monitorcidadao.core.database.entity.MandatoColunas
import dev.monitorcidadao.core.database.entity.NumerosColunas
import dev.monitorcidadao.core.database.entity.PessoaEntity
import dev.monitorcidadao.core.database.entity.PostEntity
import dev.monitorcidadao.core.database.entity.ResumoColunas
import dev.monitorcidadao.core.database.entity.SeguidoEntity
import dev.monitorcidadao.domain.Cargo
import dev.monitorcidadao.domain.Dinheiro
import dev.monitorcidadao.domain.Mandato
import dev.monitorcidadao.domain.NumerosMandato
import dev.monitorcidadao.domain.Patrimonio
import dev.monitorcidadao.domain.Pessoa
import dev.monitorcidadao.domain.PessoaId
import dev.monitorcidadao.domain.PessoaResumo
import dev.monitorcidadao.domain.Post
import dev.monitorcidadao.domain.PostBloco
import dev.monitorcidadao.domain.PostId
import dev.monitorcidadao.domain.PostTipo
import dev.monitorcidadao.domain.ResumoMandato
import java.time.format.DateTimeFormatter

// Entidade de domínio ↔ linha do banco. Linha que não fecha (cargo/data inválidos, de uma versão
// antiga) vira `null` = como se não houvesse cache; a rede repõe.

/** Sempre com segundos e fuso, igual ao contrato (o `toString()` corta `:00`). */
private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

fun Pessoa.paraEntidade(salvoEm: Long) = PessoaEntity(
    id = id.valor,
    nome = nome,
    cargo = cargo.valorApi,
    partido = partido,
    uf = uf,
    iniciais = iniciais,
    emExercicio = emExercicio,
    mandato = MandatoColunas(mandato.inicio, mandato.fim, mandato.numero),
    resumo = ResumoColunas(
        ano = resumo.ano,
        presencaPct = resumo.presencaPct,
        projetosApresentados = resumo.projetosApresentados,
        cotaCentavos = resumo.cota.centavos,
        emendasIndicadasCentavos = resumo.emendasIndicadas.centavos,
    ),
    numeros = NumerosColunas(
        presencaUltimas = numeros.presencaUltimas,
        cotaCentavos = numeros.cota.centavos,
        emendasPagasCentavos = numeros.emendasPagas.centavos,
        emendasIndicadasCentavos = numeros.emendasIndicadas.centavos,
        patrimonioTseAno = numeros.patrimonioTse.ano,
        patrimonioTseCentavos = numeros.patrimonioTse.valor.centavos,
        votouComPartidoPct = numeros.votouComPartidoPct,
        fontes = numeros.fontes,
    ),
    atualizadoEm = ISO.format(atualizadoEm),
    salvoEm = salvoEm,
)

fun PessoaEntity.paraDominioOuNull(): Pessoa? {
    val cargo = Cargo.from(cargo) ?: return null
    val atualizado = dataIso(atualizadoEm) ?: return null
    return Pessoa(
        id = PessoaId(id),
        nome = nome,
        cargo = cargo,
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
            patrimonioTse = Patrimonio(numeros.patrimonioTseAno, Dinheiro(numeros.patrimonioTseCentavos)),
            votouComPartidoPct = numeros.votouComPartidoPct,
            fontes = numeros.fontes,
        ),
        atualizadoEm = atualizado,
    )
}

fun Post.paraEntidade() = PostEntity(
    id = id.valor,
    tipo = tipo.valorApi,
    pessoaId = pessoaId.valor,
    publicadoEm = ISO.format(publicadoEm),
    titulo = titulo,
    corpo = corpo,
    blocos = blocos.map { it.paraColuna() },
    temExplicacao = temExplicacao,
)

fun PostEntity.paraDominioOuNull(): Post? {
    val data = dataIso(publicadoEm) ?: return null
    return Post(
        id = PostId(id),
        tipo = PostTipo.from(tipo),
        pessoaId = PessoaId(pessoaId),
        publicadoEm = data,
        titulo = titulo,
        corpo = corpo,
        blocos = blocos.map { it.paraDominio() },
        temExplicacao = temExplicacao,
    )
}

private fun PostBloco.paraColuna(): BlocoColuna = when (this) {
    is PostBloco.Placar -> BlocoColuna.Placar(sim, nao, abstencao)
    is PostBloco.ValorDestaque -> BlocoColuna.ValorDestaque(valor.centavos, rotulo)
    is PostBloco.Fonte -> BlocoColuna.Fonte(texto, url)
}

private fun BlocoColuna.paraDominio(): PostBloco = when (this) {
    is BlocoColuna.Placar -> PostBloco.Placar(sim, nao, abstencao)
    is BlocoColuna.ValorDestaque -> PostBloco.ValorDestaque(Dinheiro(centavos), rotulo)
    is BlocoColuna.Fonte -> PostBloco.Fonte(texto, url)
}

fun PessoaResumo.paraEntidade(seguindo: Boolean, agora: Long) =
    SeguidoEntity(id.valor, nome, cargo.valorApi, partido, uf, iniciais, seguindo, agora)

fun SeguidoEntity.paraDominioOuNull(): PessoaResumo? =
    Cargo.from(cargo)?.let { PessoaResumo(PessoaId(pessoaId), nome, it, partido, uf, iniciais) }
