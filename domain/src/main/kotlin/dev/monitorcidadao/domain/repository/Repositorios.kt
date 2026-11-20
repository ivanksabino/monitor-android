package dev.monitorcidadao.domain.repository

import dev.monitorcidadao.domain.AppException
import dev.monitorcidadao.domain.Paginator
import dev.monitorcidadao.domain.Pessoa
import dev.monitorcidadao.domain.PessoaId
import dev.monitorcidadao.domain.PessoaResumo
import dev.monitorcidadao.domain.Post
import dev.monitorcidadao.domain.PostId
import kotlinx.coroutines.flow.Flow

// Interfaces que o Domain exige; as implementações concretas ficam no Data.
// Falhas chegam como [AppException] (erro de rede nunca sai do Data).

interface PessoaRepository {
    /** Cache local do perfil; emite de novo a cada gravação. `null` = nunca baixado. */
    fun observar(id: PessoaId): Flow<Pessoa?>

    /**
     * Busca o perfil na rede e grava no cache (quem observa recebe o novo valor).
     * [forcarRede] = puxar para atualizar (não pega carona em chamada já em voo).
     * @throws AppException
     */
    suspend fun revalidar(id: PessoaId, forcarRede: Boolean = false)
}

interface PostsRepository {
    /** Linha do tempo da pessoa, paginada, com cache (stale-while-revalidate). Um por tela. */
    fun postsDaPessoa(id: PessoaId): Paginator<Post>

    /** Post já salvo no cache local (ex.: aberto a partir da lista). `null` = não está no aparelho. */
    suspend fun post(id: PostId): Post?
}

/** Pedido de seguir/deixar de seguir guardado na fila de reenvio. */
data class EnvioSeguir(val pessoa: PessoaId, val seguir: Boolean)

/**
 * Seguidos ficam só no aparelho (LGPD): o banco local é a fonte da verdade. A rede só recebe
 * o aviso (POST/DELETE), com fila de reenvio para quando estiver sem conexão.
 */
interface SeguindoRepository {
    fun observarSeguidos(): Flow<List<PessoaResumo>>

    fun observarSeguindo(id: PessoaId): Flow<Boolean>

    /** Grava a pessoa como seguida ou não (só local). */
    suspend fun marcar(pessoa: PessoaResumo, seguir: Boolean)

    /** Troca só o estado de quem já está no banco (usado para reverter). */
    suspend fun definirSeguindo(id: PessoaId, seguir: Boolean)

    /**
     * Põe na fila. [estadoNoServidor] é o que o servidor tem agora (o estado local antes do toque).
     * Um pedido novo para a mesma pessoa substitui o anterior e herda o `estadoNoServidor` dele.
     */
    suspend fun enfileirar(envio: EnvioSeguir, estadoNoServidor: Boolean)

    /** O pedido guardado para a pessoa, se houver. */
    suspend fun pendente(id: PessoaId): EnvioSeguir?

    suspend fun pendentes(): List<EnvioSeguir>

    /**
     * O servidor recebeu [envio]: sai da fila se ainda for o pedido guardado. Se um mais novo o
     * substituiu, ele continua na fila sabendo que o servidor agora está em `envio.seguir`.
     */
    suspend fun confirmarEnvio(envio: EnvioSeguir)

    /**
     * Falha definitiva de [envio]: sai da fila se ainda for o pedido guardado e devolve o estado
     * que o servidor tem (para onde reverter). `null` = um pedido mais novo o substituiu.
     */
    suspend fun descartarEnvio(envio: EnvioSeguir): Boolean?

    /**
     * POST/DELETE `/v1/seguindo/{id}`. As duas operações são idempotentes: "já seguia" (409 no
     * POST) e "já não seguia" (404 no DELETE) contam como sucesso. @throws AppException
     */
    suspend fun enviar(envio: EnvioSeguir)

    /**
     * Enquanto a lista de `GET /v1/seguindo` não tiver entrado no banco, busca e mescla (quem já
     * está no aparelho não é sobrescrito). Depois da primeira vez que dá certo, não mexe.
     * @throws AppException
     */
    suspend fun importarSeNecessario()
}
