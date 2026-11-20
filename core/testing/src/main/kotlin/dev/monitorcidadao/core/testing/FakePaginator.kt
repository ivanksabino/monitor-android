package dev.monitorcidadao.core.testing

import dev.monitorcidadao.domain.AppError
import dev.monitorcidadao.domain.PageState
import dev.monitorcidadao.domain.Paginator
import dev.monitorcidadao.domain.Post
import dev.monitorcidadao.domain.PostId
import dev.monitorcidadao.domain.PessoaId
import dev.monitorcidadao.domain.PostTipo
import dev.monitorcidadao.domain.repository.PostsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import dev.monitorcidadao.domain.StatusPagina
import java.time.OffsetDateTime

/**
 * [Paginator] em memória com o mesmo contrato do `CursorPaginator`: páginas fixas ([paginas]),
 * limiar para pedir a próxima, estados de carregando/erro/fim. [falhas] programa a resposta de
 * cada busca (em ordem; `null` = sucesso) e [segurar] prende a próxima busca até o teste liberar.
 */
class FakePaginator<Item>(
    private val paginas: List<List<Item>>,
    private val limiar: Int = 3,
) : Paginator<Item> {
    private val _state = MutableStateFlow(PageState<Item>())
    override val state: StateFlow<PageState<Item>> = _state.asStateFlow()

    /** Resposta de cada busca, consumida em ordem. Vazia = sucesso. */
    val falhas = ArrayDeque<AppError?>()
    var segurar: CompletableDeferred<Unit>? = null

    /** Índices das páginas pedidas, na ordem (0 = primeira). */
    val pedidas = mutableListOf<Int>()
    private var carregadas = 0

    override suspend fun loadFirst() {
        if (_state.value.itens.isNotEmpty()) return
        _state.value = PageState(status = StatusPagina.CarregandoPrimeira)
        buscar(0)
    }

    override suspend fun loadNextIfNeeded(atual: Item) {
        val s = _state.value
        if (s.status != StatusPagina.Ocioso || carregadas >= paginas.size) return
        val posicao = s.itens.indexOf(atual)
        if (posicao < 0 || posicao < s.itens.size - limiar) return
        _state.update { it.copy(status = StatusPagina.CarregandoMais) }
        buscar(carregadas)
    }

    override suspend fun refresh() {
        _state.update { it.copy(atualizando = true, erroRevalidacao = null) }
        carregadas = 0
        buscar(0, substituir = true)
    }

    override suspend fun retry() {
        val erro = _state.value.status as? StatusPagina.Erro ?: return
        _state.update { it.copy(status = if (erro.primeiraPagina) StatusPagina.CarregandoPrimeira else StatusPagina.CarregandoMais) }
        buscar(carregadas)
    }

    private suspend fun buscar(indice: Int, substituir: Boolean = false) {
        pedidas += indice
        segurar?.let { it.await(); segurar = null }
        val falha = if (falhas.isNotEmpty()) falhas.removeFirst() else null
        if (falha != null) {
            _state.update {
                when {
                    substituir && it.itens.isNotEmpty() -> it.copy(atualizando = false, erroRevalidacao = falha)
                    else -> it.copy(status = StatusPagina.Erro(falha, primeiraPagina = indice == 0), atualizando = false)
                }
            }
            return
        }
        carregadas = indice + 1
        val fim = carregadas >= paginas.size
        _state.update {
            val base = if (substituir || indice == 0) emptyList() else it.itens
            PageState(
                itens = base + paginas.getOrElse(indice) { emptyList() },
                status = if (fim) StatusPagina.Fim else StatusPagina.Ocioso,
            )
        }
    }
}

/** Posts fictícios para teste (mesmo formato das fixtures). */
object AmostrasPosts {
    fun post(n: Int, pessoa: PessoaId = Amostras.anaId, temExplicacao: Boolean = n % 2 == 0) = Post(
        id = PostId("post-${pessoa.valor}-$n"),
        tipo = PostTipo.Apresentou,
        pessoaId = pessoa,
        publicadoEm = OffsetDateTime.parse("2027-03-08T10:00:00-03:00"),
        titulo = "Título fictício $n",
        corpo = "Corpo fictício $n.",
        blocos = emptyList(),
        temExplicacao = temExplicacao,
    )

    /** [quantas] páginas de [porPagina] posts. */
    fun paginas(quantas: Int = 3, porPagina: Int = 4, pessoa: PessoaId = Amostras.anaId): List<List<Post>> =
        (0 until quantas).map { p -> (1..porPagina).map { post(p * porPagina + it, pessoa) } }
}

/** Um [FakePaginator] por pessoa (o primeiro criado fica em [ultimo]) e posts avulsos em [salvos]. */
class FakePostsRepository(
    private val paginas: (PessoaId) -> List<List<Post>> = { AmostrasPosts.paginas(pessoa = it) },
) : PostsRepository {
    val salvos = mutableMapOf<PostId, Post>()
    var ultimo: FakePaginator<Post>? = null
        private set

    override fun postsDaPessoa(id: PessoaId): FakePaginator<Post> =
        FakePaginator(paginas(id)).also { ultimo = it }

    override suspend fun post(id: PostId): Post? =
        salvos[id] ?: ultimo?.state?.value?.itens?.firstOrNull { it.id == id }
}
