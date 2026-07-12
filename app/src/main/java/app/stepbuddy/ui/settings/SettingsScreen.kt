package app.stepbuddy.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.stepbuddy.R
import app.stepbuddy.data.model.AppLanguage
import app.stepbuddy.data.model.UserRole
import app.stepbuddy.ui.common.BigSecondaryButton
import app.stepbuddy.ui.containerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onRoleChanged: (UserRole) -> Unit, onBack: () -> Unit) {
    val vm = containerViewModel {
        SettingsViewModel(it.settingsRepository, it.guideRepository, it.guideExporter, it.ttsManager)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val guides by vm.allGuides.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showExportPicker by remember { mutableStateOf(false) }
    var showRolePicker by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { vm.import(it) { /* success/failure could toast */ } }
    }
    val overlayLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(20.dp).verticalScroll(rememberScrollState()),
        ) {
            // Language
            Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                AppLanguage.entries.forEach { lang ->
                    FilterChip(
                        selected = state.languageCode == lang.code,
                        onClick = { vm.setLanguage(lang.code) },
                        label = { Text(lang.nativeName) },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))

            // Font size
            Text(stringResource(R.string.settings_font_size), style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_font_small), style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = state.fontScale,
                    onValueChange = { vm.setFontScale(it) },
                    valueRange = 1.0f..1.8f,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                )
                Text(stringResource(R.string.settings_font_large), style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(16.dp))

            // Speech rate
            Text(stringResource(R.string.settings_speech_rate), style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_speech_slow), style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = state.speechRate,
                    onValueChange = { vm.setSpeechRate(it) },
                    valueRange = 0.5f..1.5f,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                )
                Text(stringResource(R.string.settings_speech_fast), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(8.dp))
            BigSecondaryButton(
                text = stringResource(R.string.settings_test_voice),
                onClick = { vm.testVoice(context.getString(R.string.settings_test_voice_text)) },
            )
            Spacer(Modifier.height(24.dp))

            // Overlay
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_overlay), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.settings_overlay_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = state.overlayEnabled,
                    onCheckedChange = { enabled ->
                        vm.setOverlay(enabled)
                        if (enabled && !Settings.canDrawOverlays(context)) {
                            overlayLauncher.launch(
                                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
                            )
                        }
                    },
                )
            }
            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // Export / import
            BigSecondaryButton(stringResource(R.string.settings_export), onClick = { showExportPicker = true })
            Spacer(Modifier.height(8.dp))
            BigSecondaryButton(stringResource(R.string.settings_import), onClick = { importLauncher.launch("*/*") })
            Spacer(Modifier.height(24.dp))

            // Switch role
            BigSecondaryButton(stringResource(R.string.settings_switch_role), onClick = { showRolePicker = true })
            Spacer(Modifier.height(24.dp))

            BigSecondaryButton(stringResource(R.string.action_back), onClick = onBack)
        }
    }

    if (showExportPicker) {
        AlertDialog(
            onDismissRequest = { showExportPicker = false },
            title = { Text(stringResource(R.string.settings_export)) },
            text = {
                Column {
                    guides.forEach { guide ->
                        TextButton(onClick = {
                            val uri = vm.export(guide)
                            shareFile(context, uri)
                            showExportPicker = false
                        }) { Text("${guide.iconEmoji}  ${guide.title.ifBlank { "Untitled" }}") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExportPicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (showRolePicker) {
        AlertDialog(
            onDismissRequest = { showRolePicker = false },
            title = { Text(stringResource(R.string.settings_switch_role)) },
            text = {
                Column {
                    TextButton(onClick = { vm.setRole(UserRole.ELDERLY); showRolePicker = false; onRoleChanged(UserRole.ELDERLY) }) {
                        Text(stringResource(R.string.role_elderly_title))
                    }
                    TextButton(onClick = { vm.setRole(UserRole.CAREGIVER); showRolePicker = false; onRoleChanged(UserRole.CAREGIVER) }) {
                        Text(stringResource(R.string.role_caregiver_title))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRolePicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

private fun shareFile(context: android.content.Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/octet-stream"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, null))
}
