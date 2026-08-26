package dev.hablock.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hablock.app.ui.navigation.AppNav
import dev.hablock.app.ui.onboarding.OnboardingScreen
import dev.hablock.app.ui.theme.HablockTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { HablockTheme { GateApp() } }
    }

    override fun onStart() {
        super.onStart()
        val container = gateContainer
        container.applicationScope.launch { container.gateEngine.refreshAll() }
    }
}

@Composable
private fun GateApp() {
    val container = rememberContainer()
    val onboardingDone by container.settingsRepository.onboardingDone.collectAsStateWithLifecycle(initialValue = null)
    var justFinished by remember { mutableStateOf(false) }

    when {
        justFinished || onboardingDone == true -> AppNav()
        onboardingDone == false -> OnboardingScreen(onFinished = { justFinished = true })
    }
}
