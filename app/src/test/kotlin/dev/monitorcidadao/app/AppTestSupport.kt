package dev.monitorcidadao.app

import dev.monitorcidadao.core.testing.Amostras
import dev.monitorcidadao.core.testing.FakePessoaRepository
import dev.monitorcidadao.core.testing.FakePostsRepository
import dev.monitorcidadao.core.testing.FakeSeguindoRepository
import dev.monitorcidadao.core.testing.RecordingAnalyticsSink
import dev.monitorcidadao.domain.PessoaId
import dev.monitorcidadao.domain.repository.SeguindoRepository
import dev.monitorcidadao.domain.usecase.ObterPerfil
import dev.monitorcidadao.domain.usecase.SeguirPessoa
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.runBlocking

/** Dependências falsas do app: Ana já seguida, perfil e posts fictícios, sem rede nem banco. */
class FakeAppDependencies : AppDependencies {
    val pessoas = FakePessoaRepository().apply { revalidarCom = { Amostras.pessoa(it) } }
    val seguindo = FakeSeguindoRepository().apply { runBlocking { marcar(Amostras.resumo(), true) } }
    val analyticsSink = RecordingAnalyticsSink()

    override val analytics = analyticsSink
    override val obterPerfil = ObterPerfil(pessoas)
    override val seguirPessoa = SeguirPessoa(seguindo)
    /** Quem observa o "seguindo" de cada pessoa: um PerfilViewModel vivo = 1 observador. */
    override val seguindoRepository = ObservadoresContados(seguindo)
    override val postsRepository = FakePostsRepository()
}

/**
 * Conta as assinaturas ativas de `observarSeguindo(id)`. Quando o ViewModel de um Perfil é limpo
 * (`onCleared` cancela o `viewModelScope`), a assinatura dele termina e a contagem cai.
 */
class ObservadoresContados(private val real: SeguindoRepository) : SeguindoRepository by real {
    private val ativos = ConcurrentHashMap<PessoaId, Int>()

    fun ativos(id: PessoaId): Int = ativos[id] ?: 0

    override fun observarSeguindo(id: PessoaId): Flow<Boolean> = real.observarSeguindo(id)
        .onStart { ativos.merge(id, 1, Int::plus) }
        .onCompletion { ativos.merge(id, -1, Int::plus) }
}

/**
 * Num módulo de aplicação o manifest de teste do Compose não é mesclado (e não deve ir para o APK):
 * registra a `ComponentActivity` no PackageManager do Robolectric antes de a regra do Compose subi-la.
 */
class RegistrarComponentActivity : org.junit.rules.TestWatcher() {
    override fun starting(description: org.junit.runner.Description) {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val componente = android.content.ComponentName(app, androidx.activity.ComponentActivity::class.java)
        val pm = org.robolectric.Shadows.shadowOf(app.packageManager)
        pm.addActivityIfNotPresent(componente)
        pm.addIntentFilterForActivity(
            componente,
            android.content.IntentFilter(android.content.Intent.ACTION_MAIN).apply {
                addCategory(android.content.Intent.CATEGORY_LAUNCHER)
            },
        )
    }
}
