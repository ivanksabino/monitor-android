package dev.monitorcidadao.domain.usecase

import dev.monitorcidadao.domain.AppError
import dev.monitorcidadao.domain.AppException
import dev.monitorcidadao.domain.PessoaId
import dev.monitorcidadao.domain.PessoaResumo
import dev.monitorcidadao.domain.repository.EnvioSeguir
import dev.monitorcidadao.domain.repository.SeguindoRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Como terminou um pedido de seguir/deixar de seguir. */
sealed interface ResultadoSeguir {
    val pessoa: PessoaId

    /** O estado que a tela deve mostrar agora. */
    val seguindo: Boolean

    /** O servidor está (ou já estava) no estado que a tela mostra. */
    data class Confirmado(override val pessoa: PessoaId, override val seguindo: Boolean) : ResultadoSeguir

    /** Sem conexão: fica otimista e na fila de reenvio. */
    data class Pendente(override val pessoa: PessoaId, override val seguindo: Boolean, val erro: AppError) : ResultadoSeguir

    /** O servidor recusou: o estado voltou ao que o servidor tem. A tela anuncia (a11y §10). */
    data class Revertido(override val pessoa: PessoaId, override val seguindo: Boolean, val erro: AppError) : ResultadoSeguir
}

/**
 * Regra de seguir (contracts/api.md: otimista, reverte em erro):
 * 1. Atualização otimista: o banco local muda na hora (a tela observa o banco). Essa parte é
 *    rápida e serializada por um [Mutex] global, que nunca espera a rede.
 * 2. O pedido entra na fila antes de ir para a rede, guardando o estado que o servidor tem.
 * 3. O envio roda fora desse lock, um de cada vez por pessoa: um toque novo durante um envio
 *    lento só substitui o pedido na fila, e o laço de envio manda o mais novo em seguida.
 * 4. Sucesso → sai da fila. Sem conexão → continua na fila, estado otimista mantido.
 *    Qualquer outra falha (inclusive 429) → sai da fila e o estado volta ao do servidor.
 *
 * O app usa uma instância só (AppContainer), para que todas as telas passem pelos mesmos locks.
 */
class SeguirPessoa(private val seguindo: SeguindoRepository) {
    private val local = Mutex()
    private val enviosPorPessoa = mutableMapOf<PessoaId, Mutex>()

    suspend operator fun invoke(pessoa: PessoaResumo, seguir: Boolean): ResultadoSeguir {
        local.withLock {
            val estadoAtual = seguindo.observarSeguindo(pessoa.id).first()
            seguindo.enfileirar(EnvioSeguir(pessoa.id, seguir), estadoNoServidor = estadoAtual)
            seguindo.marcar(pessoa, seguir)
        }
        return sincronizar(pessoa.id)
    }

    /**
     * Reenvia o que ficou na fila (ex.: ao voltar a conexão ou abrir o app). Para no primeiro
     * "sem conexão": os demais falhariam do mesmo jeito.
     */
    suspend fun reenviarPendentes(): List<ResultadoSeguir> {
        val resultados = mutableListOf<ResultadoSeguir>()
        for (id in seguindo.pendentes().map { it.pessoa }.distinct()) {
            val resultado = sincronizar(id)
            resultados += resultado
            if (resultado is ResultadoSeguir.Pendente) break
        }
        return resultados
    }

    /** Envia o pedido guardado da pessoa até a fila dela esvaziar (ou ficar sem conexão). */
    private suspend fun sincronizar(id: PessoaId): ResultadoSeguir = mutexDe(id).withLock {
        var ultimo: ResultadoSeguir? = null
        while (true) {
            val envio = seguindo.pendente(id) ?: break
            when (val resultado = enviar(envio)) {
                is ResultadoSeguir.Pendente -> return@withLock resultado
                null -> Unit // substituído por um pedido mais novo: a próxima volta o envia
                else -> ultimo = resultado
            }
        }
        ultimo ?: ResultadoSeguir.Confirmado(id, seguindo.observarSeguindo(id).first())
    }

    /** `null` quando a falha é de um pedido que já foi substituído (nada a reverter). */
    private suspend fun enviar(envio: EnvioSeguir): ResultadoSeguir? = try {
        seguindo.enviar(envio)
        local.withLock { seguindo.confirmarEnvio(envio) }
        ResultadoSeguir.Confirmado(envio.pessoa, envio.seguir)
    } catch (e: AppException) {
        if (e.erro == AppError.SemConexao) {
            ResultadoSeguir.Pendente(envio.pessoa, envio.seguir, e.erro)
        } else {
            local.withLock { reverter(envio, e.erro) }
        }
    }

    /** Dentro do lock local: um toque novo não pode cair entre descartar e reverter. */
    private suspend fun reverter(envio: EnvioSeguir, erro: AppError): ResultadoSeguir? {
        val estadoNoServidor = seguindo.descartarEnvio(envio) ?: return null
        seguindo.definirSeguindo(envio.pessoa, estadoNoServidor)
        // O servidor já estava no estado pedido (ex.: tela desatualizada): nada mudou de fato.
        return if (estadoNoServidor == envio.seguir) {
            ResultadoSeguir.Confirmado(envio.pessoa, estadoNoServidor)
        } else {
            ResultadoSeguir.Revertido(envio.pessoa, estadoNoServidor, erro)
        }
    }

    private suspend fun mutexDe(id: PessoaId): Mutex = local.withLock { enviosPorPessoa.getOrPut(id) { Mutex() } }
}
