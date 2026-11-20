package dev.monitorcidadao.domain

import java.time.OffsetDateTime

// Entidades do domínio, puras (sem Android e sem conhecer o formato da API).
// O mapeamento DTO → entidade fica no módulo Data. Mesmos nomes do Domain do iOS.

/** Cargo de uma pessoa pública acompanhada. [valorApi] é o texto do contrato (`deputada_federal`). */
enum class Cargo(val valorApi: String) {
    DeputadaFederal("deputada_federal"),
    DeputadoFederal("deputado_federal"),
    Senadora("senadora"),
    Senador("senador"),
    ;

    companion object {
        private val porValor = entries.associateBy { it.valorApi }

        /** `null` quando o contrato trouxer um cargo que esta versão não conhece. */
        fun from(valorApi: String): Cargo? = porValor[valorApi]
    }
}

/** Valor em dinheiro, sempre em centavos (inteiro), como no contrato. */
@JvmInline
value class Dinheiro(val centavos: Long) : Comparable<Dinheiro> {
    override fun compareTo(other: Dinheiro): Int = centavos.compareTo(other.centavos)
}

/** Parlamentar acompanhado (resumo usado em listas, como "Quem você segue"). */
data class PessoaResumo(
    val id: PessoaId,
    val nome: String,
    val cargo: Cargo,
    val partido: String,
    val uf: String,
    val iniciais: String,
)

data class Mandato(val inicio: Int, val fim: Int, val numero: Int)

/** Resumo do mandato no ano corrente (grade "Resumo" do Perfil). */
data class ResumoMandato(
    val ano: Int,
    val presencaPct: Int,
    val projetosApresentados: Int,
    val cota: Dinheiro,
    val emendasIndicadas: Dinheiro,
)

/** Patrimônio declarado ao TSE num ano de eleição. */
data class Patrimonio(val ano: Int, val valor: Dinheiro)

/** Painel "Em números" do Perfil. */
data class NumerosMandato(
    /** Últimas sessões, da mais antiga para a mais recente (`true` = presente). */
    val presencaUltimas: List<Boolean>,
    val cota: Dinheiro,
    val emendasPagas: Dinheiro,
    val emendasIndicadas: Dinheiro,
    val patrimonioTse: Patrimonio,
    val votouComPartidoPct: Int,
    val fontes: List<String>,
) {
    val presencas: Int get() = presencaUltimas.count { it }

    /** Posições (1-based, da mais antiga) das sessões com falta. Ex.: `[6, 15]`. */
    val faltas: List<Int> get() = presencaUltimas.withIndex().filterNot { it.value }.map { it.index + 1 }
}

/** Perfil completo de um parlamentar. */
data class Pessoa(
    val id: PessoaId,
    val nome: String,
    val cargo: Cargo,
    val partido: String,
    val uf: String,
    val iniciais: String,
    val emExercicio: Boolean,
    val mandato: Mandato,
    val resumo: ResumoMandato,
    val numeros: NumerosMandato,
    val atualizadoEm: OffsetDateTime,
) {
    fun paraResumo(): PessoaResumo = PessoaResumo(id, nome, cargo, partido, uf, iniciais)
}
