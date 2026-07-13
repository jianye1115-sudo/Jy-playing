package app.stepbuddy.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import app.stepbuddy.data.model.UserRole
import app.stepbuddy.ui.caregiver.CaregiverDevicesScreen
import app.stepbuddy.ui.caregiver.CaregiverHomeScreen
import app.stepbuddy.ui.caregiver.GuideEditorScreen
import app.stepbuddy.ui.elderly.ElderlyHomeScreen
import app.stepbuddy.ui.elderly.GuidePlaybackScreen
import app.stepbuddy.ui.onboarding.OnboardingScreen
import app.stepbuddy.ui.pairing.CaregiverPairingScreen
import app.stepbuddy.ui.pairing.ElderlyPairingScreen
import app.stepbuddy.ui.settings.SettingsScreen

/**
 * Central navigation graph. Start destination is decided by the current role /
 * onboarding state (see MainActivity). Deep links (invite links, notification
 * taps) are dispatched by MainActivity onto this same NavController.
 */
@Composable
fun StepBuddyNavHost(navController: NavHostController, startDestination: String) {
    NavHost(navController = navController, startDestination = startDestination) {

        composable(Routes.ONBOARDING) {
            OnboardingScreen(onFinished = { role ->
                navController.navigate(homeRouteFor(role)) {
                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                }
            })
        }

        // ---- Elderly ----
        composable(Routes.ELDERLY_HOME) {
            ElderlyHomeScreen(
                onOpenGuide = { navController.navigate(Routes.PLAYBACK) },
                onConnect = { navController.navigate(Routes.elderlyPairing()) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.PLAYBACK) {
            GuidePlaybackScreen(onExit = { navController.popBackStack() })
        }
        composable(
            route = "${Routes.ELDERLY_PAIRING}?code={code}",
            arguments = listOf(navArgument("code") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { entry ->
            ElderlyPairingScreen(
                prefilledCode = entry.arguments?.getString("code"),
                onConnected = {
                    navController.navigate(Routes.ELDERLY_HOME) {
                        popUpTo(Routes.ELDERLY_HOME) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        // ---- Caregiver ----
        composable(Routes.CAREGIVER_HOME) {
            CaregiverHomeScreen(
                onNewGuide = { navController.navigate(Routes.guideEditor()) },
                onEditGuide = { id -> navController.navigate(Routes.guideEditor(id)) },
                onManageDevices = { navController.navigate(Routes.CAREGIVER_DEVICES) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(
            route = "${Routes.GUIDE_EDITOR}?guideId={guideId}",
            arguments = listOf(navArgument("guideId") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { entry ->
            GuideEditorScreen(
                guideId = entry.arguments?.getString("guideId"),
                onDone = { navController.popBackStack() },
                // Preview reuses the shared playback screen (works for either role).
                onPreview = { navController.navigate(Routes.PLAYBACK) },
            )
        }
        composable(Routes.CAREGIVER_DEVICES) {
            CaregiverDevicesScreen(
                onAddDevice = { navController.navigate(Routes.CAREGIVER_PAIRING) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.CAREGIVER_PAIRING) {
            CaregiverPairingScreen(onBack = { navController.popBackStack() })
        }

        // ---- Shared ----
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onRoleChanged = { role ->
                    navController.navigate(homeRouteFor(role)) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
    }
}

fun homeRouteFor(role: UserRole): String =
    if (role == UserRole.CAREGIVER) Routes.CAREGIVER_HOME else Routes.ELDERLY_HOME
