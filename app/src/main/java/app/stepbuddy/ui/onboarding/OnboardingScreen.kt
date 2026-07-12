package app.stepbuddy.ui.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.stepbuddy.R
import app.stepbuddy.data.model.AppLanguage
import app.stepbuddy.data.model.UserRole
import app.stepbuddy.ui.common.BigPrimaryButton
import app.stepbuddy.ui.common.BigSecondaryButton
import app.stepbuddy.ui.containerViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState

/**
 * First-run onboarding: choose role, pick language, grant permissions (elderly),
 * and a short tutorial. One clear action per screen; everything is large.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun OnboardingScreen(onFinished: (UserRole) -> Unit) {
    val vm = containerViewModel { OnboardingViewModel(it.settingsRepository, it.guideRepository) }

    var stepIndex by remember { mutableStateOf(0) }
    var role by remember { mutableStateOf(UserRole.UNSET) }
    var language by remember { mutableStateOf(AppLanguage.ENGLISH) }

    // Elderly gets an extra permissions step; caregiver skips it.
    val steps = remember(role) {
        buildList {
            add(OnbStep.ROLE)
            add(OnbStep.LANGUAGE)
            if (role == UserRole.ELDERLY) add(OnbStep.PERMISSIONS)
            add(OnbStep.TUTORIAL)
        }
    }
    val current = steps.getOrElse(stepIndex) { OnbStep.ROLE }

    fun advance() {
        if (stepIndex < steps.lastIndex) stepIndex++ else vm.complete(role).also { onFinished(role) }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top,
    ) {
        when (current) {
            OnbStep.ROLE -> RoleStep(role) { picked ->
                role = picked; vm.setRole(picked); advance()
            }

            OnbStep.LANGUAGE -> LanguageStep(language, onPick = { language = it; vm.setLanguage(it.code) }, onNext = ::advance)

            OnbStep.PERMISSIONS -> PermissionsStep(onNext = ::advance)

            OnbStep.TUTORIAL -> TutorialStep(role, onDone = ::advance)
        }
    }
}

private enum class OnbStep { ROLE, LANGUAGE, PERMISSIONS, TUTORIAL }

@Composable
private fun RoleStep(selected: UserRole, onPick: (UserRole) -> Unit) {
    Text(stringResource(R.string.onboarding_welcome_title), style = MaterialTheme.typography.headlineLarge)
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.onboarding_welcome_subtitle), style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(24.dp))
    Text(stringResource(R.string.onboarding_choose_role), style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(16.dp))

    RoleCard(
        emoji = "🧓",
        title = stringResource(R.string.role_elderly_title),
        desc = stringResource(R.string.role_elderly_desc),
        selected = selected == UserRole.ELDERLY,
        onClick = { onPick(UserRole.ELDERLY) },
    )
    Spacer(Modifier.height(16.dp))
    RoleCard(
        emoji = "👨‍👩‍👧",
        title = stringResource(R.string.role_caregiver_title),
        desc = stringResource(R.string.role_caregiver_desc),
        selected = selected == UserRole.CAREGIVER,
        onClick = { onPick(UserRole.CAREGIVER) },
    )
}

@Composable
private fun RoleCard(emoji: String, title: String, desc: String, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(emoji, style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(desc, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun LanguageStep(selected: AppLanguage, onPick: (AppLanguage) -> Unit, onNext: () -> Unit) {
    Text(stringResource(R.string.onboarding_pick_language), style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(16.dp))
    AppLanguage.entries.forEach { lang ->
        Card(
            modifier = Modifier.fillMaxWidth()
                .padding(vertical = 6.dp)
                .selectable(selected = selected == lang, onClick = { onPick(lang) }),
            colors = CardDefaults.cardColors(
                containerColor = if (selected == lang) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("${lang.nativeName}  (${lang.displayName})", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
    Spacer(Modifier.height(24.dp))
    BigPrimaryButton(stringResource(R.string.onboarding_continue), onClick = onNext)
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun PermissionsStep(onNext: () -> Unit) {
    val context = LocalContext.current

    // Notifications (Android 13+). On older versions this reports granted.
    val notifPermission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
        else null

    // Overlay: a separate system settings screen, launched on request.
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    val overlayLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        overlayGranted = Settings.canDrawOverlays(context)
    }

    Text(stringResource(R.string.perm_title), style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(16.dp))

    Text(stringResource(R.string.perm_notifications_title), style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(4.dp))
    Text(stringResource(R.string.perm_notifications_desc), style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(8.dp))
    BigPrimaryButton(
        text = stringResource(R.string.perm_grant),
        onClick = { notifPermission?.launchPermissionRequest() },
    )

    Spacer(Modifier.height(24.dp))
    Text(stringResource(R.string.perm_overlay_title), style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(4.dp))
    Text(stringResource(R.string.perm_overlay_desc), style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(8.dp))
    BigSecondaryButton(
        text = if (overlayGranted) stringResource(R.string.pairing_connected) else stringResource(R.string.perm_grant),
        enabled = !overlayGranted,
        onClick = {
            overlayLauncher.launch(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
            )
        },
    )

    Spacer(Modifier.height(32.dp))
    BigPrimaryButton(stringResource(R.string.onboarding_continue), onClick = onNext)
    Spacer(Modifier.height(8.dp))
    BigSecondaryButton(stringResource(R.string.perm_skip), onClick = onNext)
}

@Composable
private fun TutorialStep(role: UserRole, onDone: () -> Unit) {
    Text(stringResource(R.string.onboarding_tutorial_title), style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(16.dp))
    Text(
        if (role == UserRole.CAREGIVER) stringResource(R.string.onboarding_tutorial_caregiver)
        else stringResource(R.string.onboarding_tutorial_elderly),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Start,
    )
    Spacer(Modifier.height(32.dp))
    BigPrimaryButton(stringResource(R.string.onboarding_get_started), onClick = onDone)
}
