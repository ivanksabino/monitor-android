package dev.monitorcidadao.data.repository.paging

import dev.monitorcidadao.domain.AppError
import dev.monitorcidadao.domain.AppException
import dev.monitorcidadao.domain.PageState
import dev.monitorcidadao.domain.Pagina
import dev.monitorcidadao.domain.Paginator
import dev.monitorcidadao.domain.StatusPagina
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** De onde o [CursorPaginator] tira as páginas: o banco (cache) e a rede (que também grava no banco). */
interface PageSource<Item> {
    /** Tudo o que está salvo, na ordem, com o cursor para continuar. `null` = nada salvo. */
    suspend fun cached(): Pagina<Item>?

    /** Busca uma página na rede e grava no cache. `cursor == null` = primeira. @throws AppException */
    suspend fun fetch(cursor: String?, forcarRede: Boolean): Pagina<Item>
}

/**
 * [Paginator] genérico por cursor (arquitetura §6), igual nos dois apps:
 * - `loadFirst()`: mostra o cache na hora e revalida a primeira página (stale-while-revalidate);
 * - `loadNextIfNeeded(atual)`: pede a próxima quando [atual] está a [limiar] itens do fim;
 * - trava de página duplicada: uma carga por vez e cursor já consumido nunca é pedido de novo
 *   (servidor em loop vira fim de lista). Um `loadNextIfNeeded` que chega durante outra carga
 *   não dispara nada na hora, mas fica guardado e é reavaliado quando essa carga termina (ex.:
 *   chegou ao fim da lista do cache enquanto a revalidação ainda rodava);
 * - dedup por id ([idOf]) ao juntar páginas;
 * - estados carregando / erro (primeira página ou rodapé) / fim; `retry()` repete o que falhou.
 */
class CursorPaginator<Item>(
    private val source: PageSource<Item>,
    private val idOf: (Item) -> Any,
    private val limiar: Int = LIMIAR_PADRAO,
) : Paginator<Item> {
    private val _state = MutableStateFlow(PageState<Item>())
    override val state: StateFlow<PageState<Item>> = _state.asStateFlow()

    private val mutex = Mutex()
    private var proximoCursor: String? = null
    private val cursoresConsumidos = mutableSetOf<String>()

    /** Último item que pediu a próxima página enquanto outra carga segurava o [mutex]. */
    @Volatile private var pedidoAdiado: Item? = null

    override suspend fun loadFirst() = mutex.withLock {
        val atual = _state.value
        // Já carregado da rede (ex.: a tela reapareceu): nada a fazer.
        if (atual.itens.isNotEmpty() && !atual.doCache) return@withLock
        if (atual.itens.isEmpty()) {
            val cache = lerCache()
            _state.value = if (cache != null && cache.itens.isNotEmpty()) {
                proximoCursor = cache.proximoCursor
                PageState(itens = cache.itens.distinctBy(idOf), status = statusApos(cache.proximoCursor), doCache = true)
            } else {
                PageState(status = StatusPagina.CarregandoPrimeira)
            }
        }
        carregarPrimeira(forcarRede = false)
        atenderPedidoAdiado()
    }

    override suspend fun loadNextIfNeeded(atual: Item) {
        if (!precisaDaProxima(atual)) return
        // Guarda antes do tryLock: se quem segura o lock terminar entre as duas linhas, ele já vê o pedido.
        pedidoAdiado = atual
        if (!mutex.tryLock()) return // há uma carga em andamento: ela atende o pedido ao terminar
        try {
            pedidoAdiado = null
            if (precisaDaProxima(atual)) carregarProxima(proximoCursor!!)
        } finally {
            mutex.unlock()
        }
    }

    /** Dentro do lock, ao fim de uma carga: o item que pediu continua perto do fim da lista nova? */
    private suspend fun atenderPedidoAdiado() {
        val item = pedidoAdiado ?: return
        pedidoAdiado = null
        if (precisaDaProxima(item)) carregarProxima(proximoCursor!!)
    }

    override suspend fun refresh() = mutex.withLock {
        _state.update {
            if (it.itens.isEmpty()) PageState(status = StatusPagina.CarregandoPrimeira)
            else it.copy(atualizando = true, erroRevalidacao = null)
        }
        carregarPrimeira(forcarRede = true)
        atenderPedidoAdiado()
    }

    override suspend fun retry() = mutex.withLock {
        val status = _state.value.status as? StatusPagina.Erro ?: return@withLock
        if (status.primeiraPagina) {
            _state.update { it.copy(status = StatusPagina.CarregandoPrimeira) }
            carregarPrimeira(forcarRede = false)
        } else {
            proximoCursor?.let { carregarProxima(it) }
        }
        atenderPedidoAdiado()
    }

    private fun precisaDaProxima(atual: Item): Boolean {
        val s = _state.value
        if (s.status != StatusPagina.Ocioso || s.atualizando || proximoCursor == null) return false
        val id = idOf(atual)
        val posicao = s.itens.indexOfFirst { idOf(it) == id }
        return posicao >= 0 && posicao >= s.itens.size - limiar
    }

    private suspend fun carregarPrimeira(forcarRede: Boolean) {
        try {
            val pagina = source.fetch(null, forcarRede)
            cursoresConsumidos.clear()
            proximoCursor = pagina.proximoCursor
            _state.value = PageState(itens = pagina.itens.distinctBy(idOf), status = statusApos(proximoCursor))
        } catch (e: CancellationException) {
            // Volta a um estado de onde `loadFirst`/`refresh` conseguem recomeçar.
            _state.update {
                if (it.itens.isEmpty()) PageState() else it.copy(atualizando = false, status = it.status.semCarregando(proximoCursor))
            }
            throw e
        } catch (e: Exception) {
            val erro = e.comoAppError()
            _state.update {
                if (it.itens.isEmpty()) PageState(status = StatusPagina.Erro(erro, primeiraPagina = true))
                else it.copy(atualizando = false, erroRevalidacao = erro)
            }
        }
    }

    private suspend fun carregarProxima(cursor: String) {
        _state.update { it.copy(status = StatusPagina.CarregandoMais) }
        try {
            val pagina = source.fetch(cursor, forcarRede = false)
            cursoresConsumidos += cursor
            proximoCursor = pagina.proximoCursor?.takeUnless { it in cursoresConsumidos }
            _state.update {
                it.copy(itens = (it.itens + pagina.itens).distinctBy(idOf), status = statusApos(proximoCursor))
            }
        } catch (e: CancellationException) {
            _state.update { it.copy(status = StatusPagina.Ocioso) }
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(status = StatusPagina.Erro(e.comoAppError(), primeiraPagina = false)) }
        }
    }

    /** Cache é melhor esforço: banco ilegível = sem cache. */
    private suspend fun lerCache(): Pagina<Item>? = try {
        source.cached()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private fun statusApos(cursor: String?): StatusPagina = if (cursor == null) StatusPagina.Fim else StatusPagina.Ocioso

    private fun StatusPagina.semCarregando(cursor: String?): StatusPagina = when (this) {
        StatusPagina.CarregandoPrimeira, StatusPagina.CarregandoMais -> statusApos(cursor)
        else -> this
    }

    private fun Exception.comoAppError(): AppError = (this as? AppException)?.erro ?: AppError.Desconhecido

    companion object {
        /** Faltando este número de itens para o fim, a próxima página é pedida. */
        const val LIMIAR_PADRAO = 3
    }
}
