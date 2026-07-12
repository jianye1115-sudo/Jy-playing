package app.stepbuddy

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.map
import app.stepbuddy.data.model.UserRole
import app.stepbuddy.data.model.UserSettings
import app.stepbuddy.ui.navigation.Routes
import app.stepbuddy.ui.navigation.StepBuddyNavHost
import app.stepbuddy.ui.navigation.homeRouteFor
import app.stepbuddy.ui.pairing.PairingUtils
import app.stepbuddy.ui.theme.StepBuddyTheme

/**
 * Single-activity host. Chooses the start destination from onboarding/role, and
 * dispatches deep links onto the nav graph:
 *  - INVITE LINK (App Link) https://stepbuddy.app/pair?code=123456  → pre-filled
 *    elderly pairing screen (one-tap Connect). Also handles stepbuddy://pair.
 *  - Notification tap (ACTION_OPEN_PLAYBACK) → back to the playback screen.
 */
class MainActivity : ComponentActivity() {

    // Compose-observable holders updated from onCreate/onNewIntent.
    private val pendingPairCode = mutableStateOf<String?>(null)
    private val openPlayback = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)

        setContent {
            val container = (application as StepBuddyApp).container
            // Map to a nullable flow so we can show a splash until the first
            // real value arrives (null = not yet loaded).
            val settings by remember(container) {
                container.settingsRepository.settings.map { it as UserSettings? }
            }.collectAsStateWithLifecycle(initialValue = null)

            val current = settings
            StepBuddyTheme(fontScale = current?.fontScale ?: 1.3f) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (current == null) {
                        SplashBox()
                    } else {
                        val navController = rememberNavController()
                        StepBuddyNavHost(navController, startDestination = startRouteFor(current))

                        // Deep link: open pre-filled pairing, then clear so it
                        // doesn't re-fire on recomposition/rotation.
                        val code = pendingPairCode.value
                        LaunchedEffect(code) {
                            if (code != null) {
                                navController.navigate(Routes.elderlyPairing(code))
                                pendingPairCode.value = null
                            }
                        }
                        LaunchedEffect(openPlayback.value) {
                            if (openPlayback.value) {
                                navController.navigate(Routes.PLAYBACK)
                                openPlayback.value = false
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_VIEW -> PairingUtils.parseCode(intent.dataString)?.let { pendingPairCode.value = it }
            ACTION_OPEN_PLAYBACK -> openPlayback.value = true
        }
    }

    private fun startRouteFor(settings: UserSettings): String = when {
        !settings.onboarded -> Routes.ONBOARDING
        else -> homeRouteFor(settings.role.takeIf { it != UserRole.UNSET } ?: UserRole.ELDERLY)
    }

    companion object {
        const val ACTION_OPEN_PLAYBACK = "app.stepbuddy.action.OPEN_PLAYBACK"
    }
}

@androidx.compose.runtime.Composable
private fun SplashBox() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("👣 StepBuddy", style = MaterialTheme.typography.headlineLarge)
    }
}
