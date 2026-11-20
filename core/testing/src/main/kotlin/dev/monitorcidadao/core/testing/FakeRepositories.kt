package dev.monitorcidadao.core.testing

import dev.monitorcidadao.domain.AppError
import dev.monitorcidadao.domain.AppException
import dev.monitorcidadao.domain.Cargo
import dev.monitorcidadao.domain.Dinheiro
import dev.monitorcidadao.domain.Mandato
import dev.monitorcidadao.domain.NumerosMandato
import dev.monitorcidadao.domain.Patrimonio
import dev.monitorcidadao.domain.Pessoa
import dev.monitorcidadao.domain.PessoaId
import dev.monitorcidadao.domain.PessoaResumo
import dev.monitorcidadao.domain.ResumoMandato
import dev.monitorcidadao.domain.repository.EnvioSeguir
import dev.monitorcidadao.domain.repository.PessoaRepository
import dev.monitorcidadao.domain.repository.SeguindoRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.OffsetDateTime

/** Entidades fictícias prontas para teste (mesmos dados das fixtures). */
object Amostras {
    val anaId = PessoaId("cam-1001")

    fun pessoa(id: PessoaId = anaId, nome: String = "Ana Ribeiro", presencaPct: Int = 92) = Pessoa(
        id = id,
        nome = nome,
        cargo = Cargo.DeputadaFederal,
        partido = "PEX",
        uf = "PB",
        iniciais = "AR",
        emExercicio = true,
        mandato = Mandato(2027, 2031, 1),
        resumo = ResumoMandato(2027, presencaPct, 14, Dinheiro(11_234_018), Dinheiro(840_000_000)),
        numeros = NumerosMandato(
            presencaUltimas = List(20) { it != 5 && it != 14 },
            cota = Dinheiro(11_234_018),
            emendasPagas = Dinheiro(310_000_000),
            emendasIndicadas = Dinheiro(840_000_000),
            patrimonioTse = Patrimonio(2026, Dinheiro(124_000_000)),
            votouComPartidoPct = 94,
            fontes = listOf("Câmara dos Deputados", "TSE", "Portal da Transparência"),
        ),
        atualizadoEm = OffsetDateTime.parse("2027-03-12T18:40:00-03:00"),
    )

    fun resumo(id: PessoaId = anaId, nome: String = "Ana Ribeiro") =
        PessoaResumo(id, nome, Cargo.DeputadaFederal, "PEX", "PB", "AR")
}

/** Cache em memória + rede programável. [revalidarCom] decide o que a "rede" faz. */
class FakePessoaRepository(inicial: Map<PessoaId, Pessoa> = emptyMap()) : PessoaRepository {
    private val cache = MutableStateFlow(inicial)
    var revalidarCom: suspend (PessoaId) -> Pessoa = { throw AppException(AppError.SemConexao) }
    val revalidacoes = mutableListOf<Pair<PessoaId, Boolean>>()

    override fun observar(id: PessoaId): Flow<Pessoa?> = cache.map { it[id] }.distinctUntilChanged()

    override suspend fun revalidar(id: PessoaId, forcarRede: Boolean) {
        revalidacoes += id to forcarRede
        val nova = revalidarCom(id)
        cache.update { it + (id to nova) }
    }
}

/**
 * Seguidos e fila em memória, com as mesmas regras da fila real. A "rede" responde com
 * [respostas] (uma por envio, em ordem) e, quando acabam, com [respostaDoServidor].
 */
class FakeSeguindoRepository : SeguindoRepository {
    private data class Pedido(val envio: EnvioSeguir, val estadoNoServidor: Boolean)

    private val seguidos = MutableStateFlow<Map<PessoaId, Pair<PessoaResumo, Boolean>>>(emptyMap())
    private val fila = linkedMapOf<PessoaId, Pedido>()

    /** `null` = sucesso; senão o erro que a rede devolve. */
    var respostaDoServidor: AppError? = null

    /** Respostas de cada envio, consumidas em ordem antes de [respostaDoServidor]. */
    val respostas = ArrayDeque<AppError?>()

    /** Se definido, o envio espera este sinal (para o teste ver o estado otimista). */
    var segurarEnvio: CompletableDeferred<Unit>? = null
    val enviados = mutableListOf<EnvioSeguir>()

    val filaAtual: List<EnvioSeguir> get() = fila.values.map { it.envio }

    fun estadoNoServidorDaFila(id: PessoaId): Boolean? = fila[id]?.estadoNoServidor

    override fun observarSeguidos(): Flow<List<PessoaResumo>> =
        seguidos.map { m -> m.values.filter { it.second }.map { it.first } }.distinctUntilChanged()

    override fun observarSeguindo(id: PessoaId): Flow<Boolean> =
        seguidos.map { it[id]?.second ?: false }.distinctUntilChanged()

    override suspend fun marcar(pessoa: PessoaResumo, seguir: Boolean) {
        seguidos.update { it + (pessoa.id to (pessoa to seguir)) }
    }

    override suspend fun definirSeguindo(id: PessoaId, seguir: Boolean) {
        seguidos.update { m -> m[id]?.let { m + (id to (it.first to seguir)) } ?: m }
    }

    override suspend fun enfileirar(envio: EnvioSeguir, estadoNoServidor: Boolean) {
        val anterior = fila.remove(envio.pessoa)
        fila[envio.pessoa] = Pedido(envio, anterior?.estadoNoServidor ?: estadoNoServidor)
    }

    override suspend fun pendente(id: PessoaId): EnvioSeguir? = fila[id]?.envio

    override suspend fun pendentes(): List<EnvioSeguir> = filaAtual

    override suspend fun confirmarEnvio(envio: EnvioSeguir) {
        val atual = fila[envio.pessoa] ?: return
        if (atual.envio == envio) fila.remove(envio.pessoa) else fila[envio.pessoa] = atual.copy(estadoNoServidor = envio.seguir)
    }

    override suspend fun descartarEnvio(envio: EnvioSeguir): Boolean? {
        val atual = fila[envio.pessoa]?.takeIf { it.envio == envio } ?: return null
        fila.remove(envio.pessoa)
        return atual.estadoNoServidor
    }

    override suspend fun enviar(envio: EnvioSeguir) {
        segurarEnvio?.await()
        enviados += envio
        val resposta = if (respostas.isNotEmpty()) respostas.removeFirst() else respostaDoServidor
        resposta?.let { throw AppException(it) }
    }

    override suspend fun importarSeNecessario() = Unit
}
