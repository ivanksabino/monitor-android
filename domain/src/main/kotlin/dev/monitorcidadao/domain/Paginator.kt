package dev.monitorcidadao.domain

import kotlinx.coroutines.flow.StateFlow

/**
 * Lista paginada por cursor (arquitetura §6). O contrato mora no Domain para a feature recebê-lo
 * pela factory sem conhecer o Data; a implementação (`CursorPaginator`) fica no Data.
 *
 * Todas as operações são `suspend`: quem chama (o `viewModelScope`) é dono do cancelamento.
 */
interface Paginator<Item> {
    val state: StateFlow<PageState<Item>>

    /** Mostra o cache (se houver) e revalida a primeira página na rede. */
    suspend fun loadFirst()

    /** Carrega a próxima página se [atual] estiver a menos do limiar do fim da lista. */
    suspend fun loadNextIfNeeded(atual: Item)

    /** Puxar para atualizar: busca a primeira página na rede e descarta o resto. */
    suspend fun refresh()

    /** "Tentar de novo" depois de um erro (primeira página ou página seguinte). */
    suspend fun retry()
}

data class PageState<Item>(
    val itens: List<Item> = emptyList(),
    val status: StatusPagina = StatusPagina.Ocioso,
    /** Os itens vieram do banco e a revalidação na rede ainda não confirmou (ou falhou). */
    val doCache: Boolean = false,
    /** Puxar para atualizar em andamento, com os itens atuais ainda na tela. */
    val atualizando: Boolean = false,
    /** A revalidação (ou o puxar para atualizar) falhou, mas há itens na tela: aviso discreto. */
    val erroRevalidacao: AppError? = null,
) {
    val fim: Boolean get() = status == StatusPagina.Fim
}

sealed interface StatusPagina {
    /** Pronto para pedir a próxima página. */
    data object Ocioso : StatusPagina

    /** Primeira carga sem nada para mostrar (esqueleto). */
    data object CarregandoPrimeira : StatusPagina

    /** Rodapé "Carregando mais posts". */
    data object CarregandoMais : StatusPagina

    /** Falhou; [primeiraPagina] diz se é o erro de tela cheia ou o do rodapé. */
    data class Erro(val erro: AppError, val primeiraPagina: Boolean) : StatusPagina

    /** Não há mais páginas ("Você viu todos os posts"). */
    data object Fim : StatusPagina
}
