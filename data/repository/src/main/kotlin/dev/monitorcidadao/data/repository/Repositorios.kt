package dev.monitorcidadao.data.repository

import dev.monitorcidadao.core.database.dao.FeedDao
import dev.monitorcidadao.core.database.dao.FilaEnvioDao
import dev.monitorcidadao.core.database.dao.PessoaDao
import dev.monitorcidadao.core.database.dao.SeguidoDao
import dev.monitorcidadao.core.network.HTTPClient
import dev.monitorcidadao.core.network.NetworkError
import dev.monitorcidadao.data.api.Endpoints
import dev.monitorcidadao.data.api.IdInvalidoException
import dev.monitorcidadao.data.repository.mapping.paraDominio
import dev.monitorcidadao.data.repository.mapping.paraDominioOuNull
import dev.monitorcidadao.data.repository.mapping.paraEntidade
import dev.monitorcidadao.data.repository.paging.CursorPaginator
import dev.monitorcidadao.data.repository.paging.PageSource
import dev.monitorcidadao.domain.AppError
import dev.monitorcidadao.domain.AppException
import dev.monitorcidadao.domain.PaginaPosts
import dev.monitorcidadao.domain.Paginator
import dev.monitorcidadao.domain.Pessoa
import dev.monitorcidadao.domain.PessoaId
import dev.monitorcidadao.domain.PessoaResumo
import dev.monitorcidadao.domain.Post
import dev.monitorcidadao.domain.PostId
import dev.monitorcidadao.domain.repository.EnvioSeguir
import dev.monitorcidadao.domain.repository.PessoaRepository
import dev.monitorcidadao.domain.repository.PostsRepository
import dev.monitorcidadao.domain.repository.SeguindoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Relógio injetável (epoch millis) para os carimbos `salvo_em`/`criado_em`. */
typealias Relogio = () -> Long

/**
 * Erro de rede nunca sai do Data: vira [AppException] com o [AppError]. Id que não cabe num
 * segmento de URL não pode existir no servidor: vira [AppError.NaoEncontrado] sem ir à rede.
 */
internal suspend inline fun <T> comErroDeApp(block: () -> T): T = try {
    block()
} catch (e: NetworkError) {
    throw AppException(e.toAppError(), e)
} catch (e: IdInvalidoException) {
    throw AppException(AppError.NaoEncontrado, e)
}

/** Falha do banco (disco cheio, banco travado...) também chega como [AppException], como a interface promete. */
internal suspend inline fun <T> comErroDeBanco(block: () -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: AppException) {
    throw e
} catch (e: Exception) {
    throw AppException(AppError.Desconhecido, e)
}

/** Perfil: o banco é a fonte que a tela observa; a rede só atualiza o banco. */
class DefaultPessoaRepository(
    private val http: HTTPClient,
    private val dao: PessoaDao,
    private val relogio: Relogio = System::currentTimeMillis,
) : PessoaRepository {

    override fun observar(id: PessoaId): Flow<Pessoa?> =
        dao.observar(id.valor).map { it?.paraDominioOuNull() }.distinctUntilChanged()

    override suspend fun revalidar(id: PessoaId, forcarRede: Boolean) {
        val pessoa = comErroDeApp { http.send(Endpoints.pessoa(id.valor, forcarRede)) }.paraDominio()
        // A tela observa o banco: sem gravar, a revalidação não chega a ela. Por isso não é
        // melhor esforço como no feed, e a falha vira erro de revalidação.
        comErroDeBanco { dao.salvar(pessoa.paraEntidade(relogio())) }
    }
}

/** Linha do tempo: cada pessoa tem seu feed no banco (`feed_pagina`), servido antes da rede. */
class DefaultPostsRepository(
    private val http: HTTPClient,
    private val feedDao: FeedDao,
    private val relogio: Relogio = System::currentTimeMillis,
    private val limiar: Int = CursorPaginator.LIMIAR_PADRAO,
) : PostsRepository {

    override fun postsDaPessoa(id: PessoaId): Paginator<Post> =
        CursorPaginator(FonteDePostsDaPessoa(id), idOf = Post::id, limiar = limiar)

    override suspend fun post(id: PostId): Post? = comErroDeBanco { feedDao.post(id.valor) }?.paraDominioOuNull()

    private inner class FonteDePostsDaPessoa(private val pessoa: PessoaId) : PageSource<Post> {
        private val feed = "pessoa:${pessoa.valor}"

        override suspend fun cached(): PaginaPosts? =
            feedDao.ler(feed)?.let { salvo -> PaginaPosts(salvo.posts.mapNotNull { it.paraDominioOuNull() }, salvo.proximoCursor) }

        override suspend fun fetch(cursor: String?, forcarRede: Boolean): PaginaPosts {
            val pagina = comErroDeApp { http.send(Endpoints.postsDaPessoa(pessoa.valor, cursor, forcarRede)) }.paraDominio()
            salvarSemFalhar(cursor, pagina)
            return pagina
        }

        /** O cache é melhor esforço: falha ao gravar não impede de mostrar a página. */
        private suspend fun salvarSemFalhar(cursor: String?, pagina: PaginaPosts) {
            try {
                feedDao.salvarPagina(feed, cursor, pagina.proximoCursor, pagina.itens.map { it.paraEntidade() }, relogio())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // segue sem cache desta página
            }
        }
    }
}

/**
 * Seguidos só no aparelho (LGPD): `seguido` é a fonte da verdade; a rede recebe o aviso
 * (POST/DELETE) e `fila_envio` guarda o que falta enviar, com o estado que o servidor tem.
 */
class DefaultSeguindoRepository(
    private val http: HTTPClient,
    private val seguidoDao: SeguidoDao,
    private val filaDao: FilaEnvioDao,
    private val relogio: Relogio = System::currentTimeMillis,
) : SeguindoRepository {

    override fun observarSeguidos(): Flow<List<PessoaResumo>> =
        seguidoDao.observarSeguidos().map { lista -> lista.mapNotNull { it.paraDominioOuNull() } }.distinctUntilChanged()

    override fun observarSeguindo(id: PessoaId): Flow<Boolean> =
        seguidoDao.observarSeguindo(id.valor).map { it ?: false }.distinctUntilChanged()

    override suspend fun marcar(pessoa: PessoaResumo, seguir: Boolean) =
        seguidoDao.salvar(pessoa.paraEntidade(seguir, relogio()))

    override suspend fun definirSeguindo(id: PessoaId, seguir: Boolean) =
        seguidoDao.definirSeguindo(id.valor, seguir, relogio())

    override suspend fun enfileirar(envio: EnvioSeguir, estadoNoServidor: Boolean) =
        filaDao.enfileirar(envio.pessoa.valor, envio.seguir, estadoNoServidor, relogio())

    override suspend fun pendente(id: PessoaId): EnvioSeguir? =
        filaDao.pendente(id.valor)?.let { EnvioSeguir(PessoaId(it.pessoaId), it.seguir) }

    override suspend fun pendentes(): List<EnvioSeguir> =
        filaDao.pendentes().map { EnvioSeguir(PessoaId(it.pessoaId), it.seguir) }

    override suspend fun confirmarEnvio(envio: EnvioSeguir) = filaDao.confirmar(envio.pessoa.valor, envio.seguir)

    override suspend fun descartarEnvio(envio: EnvioSeguir): Boolean? = filaDao.descartar(envio.pessoa.valor, envio.seguir)

    override suspend fun enviar(envio: EnvioSeguir) {
        val endpoint = if (envio.seguir) Endpoints.seguir(envio.pessoa.valor) else Endpoints.deixarDeSeguir(envio.pessoa.valor)
        try {
            comErroDeApp { http.send(endpoint) }
        } catch (e: AppException) {
            if (!jaEstavaAssim(envio, e)) throw e
        }
    }

    /** POST e DELETE são idempotentes: "já seguia" (409) e "já não seguia" (404) são sucesso. */
    private fun jaEstavaAssim(envio: EnvioSeguir, e: AppException): Boolean {
        val status = (e.cause as? NetworkError.Http)?.status ?: return false
        return if (envio.seguir) status == HTTP_CONFLITO else status == HTTP_NAO_ENCONTRADO
    }

    override suspend fun importarSeNecessario() {
        if (seguidoDao.importado()) return
        val itens = comErroDeApp { http.send(Endpoints.seguindo()) }.itens.mapNotNull { it.paraDominioOuNull() }
        // Mescla: quem já está no aparelho (seguiu ou deixou de seguir, até durante este GET) não muda.
        seguidoDao.importar(itens.map { it.paraEntidade(seguindo = true, agora = relogio()) })
    }

    private companion object {
        const val HTTP_CONFLITO = 409
        const val HTTP_NAO_ENCONTRADO = 404
    }
}
