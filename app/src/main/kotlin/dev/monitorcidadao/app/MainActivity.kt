package dev.monitorcidadao.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import dev.monitorcidadao.core.designsystem.MonitorTheme

/** Guarda a navegação (routers das abas) entre recriações da Activity. */
class ShellViewModel : ViewModel() {
    val navigation = AppNavigation()
}

class MainActivity : ComponentActivity() {
    private val shell: ShellViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val factory = AppFactory((application as MonitorApplication).container)
        // Deep link de abertura (monitorcidadao://perfil/{id}); na recriação a pilha já está no ShellViewModel.
        if (savedInstanceState == null) shell.navigation.abrir(intent?.dataString)
        setContent {
            MonitorTheme {
                MonitorApp(shell.navigation, factory)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        shell.navigation.abrir(intent.dataString)
    }
}
