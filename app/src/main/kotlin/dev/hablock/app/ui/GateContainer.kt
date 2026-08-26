package dev.hablock.app.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.hablock.app.HablockApplication
import dev.hablock.app.di.AppContainer

val Context.gateContainer: AppContainer
    get() = (applicationContext as HablockApplication).container

@Composable
fun rememberContainer(): AppContainer {
    val context = LocalContext.current
    return remember(context) { context.gateContainer }
}

@Composable
inline fun <reified VM : ViewModel> gateViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM = viewModel(
    key = key,
    factory = viewModelFactory {
        initializer {
            val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as HablockApplication
            create(application.container)
        }
    },
)
