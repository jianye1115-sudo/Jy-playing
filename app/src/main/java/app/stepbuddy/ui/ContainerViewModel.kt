package app.stepbuddy.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.stepbuddy.StepBuddyApp
import app.stepbuddy.di.AppContainer

/**
 * Bridges Compose ViewModels to the manual [AppContainer]. Instead of Hilt, a
 * screen calls `containerViewModel { container -> MyViewModel(container.repo) }`.
 */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = (LocalContext.current.applicationContext as StepBuddyApp).container
    return viewModel(factory = viewModelFactory { initializer { create(container) } })
}
