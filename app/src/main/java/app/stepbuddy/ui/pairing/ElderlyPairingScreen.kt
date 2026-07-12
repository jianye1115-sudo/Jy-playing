package app.stepbuddy.ui.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.stepbuddy.R
import app.stepbuddy.ui.common.BigPrimaryButton
import app.stepbuddy.ui.common.BigSecondaryButton
import app.stepbuddy.ui.containerViewModel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/**
 * Elderly connect screen. When reached from an invite link the code arrives
 * pre-filled and the whole flow collapses to ONE big "Connect" button — exactly
 * the "single visible action" the accessibility rules ask for.
 */
@Composable
fun ElderlyPairingScreen(
    prefilledCode: String?,
    onConnected: () -> Unit,
    onBack: () -> Unit,
) {
    val vm = containerViewModel {
        ElderlyPairingViewModel(it.pairingRepository, it.firebaseSync, it.settingsRepository)
    }
    val state by vm.state.collectAsStateWithLifecycle()

    var code by remember { mutableStateOf(prefilledCode?.filter { it.isDigit() }?.take(6).orEmpty()) }
    val prefilled = !prefilledCode.isNullOrBlank()

    // QR scanning via zxing-android-embedded.
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        PairingUtils.parseCode(result.contents)?.let { code = it }
    }

    LaunchedEffect(state) {
        if (state is ConnectState.Success) onConnected()
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Text(stringResource(R.string.pairing_elderly_title), style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(12.dp))
        Text(
            if (prefilled) stringResource(R.string.pairing_prefilled_hint)
            else stringResource(R.string.pairing_elderly_desc),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = code,
            onValueChange = { code = it.filter { c -> c.isDigit() }.take(6) },
            label = { Text(stringResource(R.string.pairing_enter_code)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            textStyle = MaterialTheme.typography.displaySmall.copy(textAlign = TextAlign.Center),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        // Secondary path: scan the square code (hidden emphasis when pre-filled).
        if (!prefilled) {
            BigSecondaryButton(
                text = stringResource(R.string.pairing_scan_qr),
                icon = Icons.Filled.QrCodeScanner,
                onClick = {
                    scanLauncher.launch(
                        ScanOptions().setBeepEnabled(false).setPrompt("").setOrientationLocked(false),
                    )
                },
            )
            Spacer(Modifier.height(24.dp))
        }

        BigPrimaryButton(
            text = when (state) {
                ConnectState.Connecting -> stringResource(R.string.pairing_connecting)
                else -> stringResource(R.string.pairing_connect)
            },
            enabled = code.length == 6 && state != ConnectState.Connecting,
            onClick = { vm.connect(code) },
        )

        Spacer(Modifier.height(16.dp))
        when (val s = state) {
            is ConnectState.Error -> Text(
                stringResource(s.messageRes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            ConnectState.Success -> Text(
                stringResource(R.string.pairing_success),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            else -> Unit
        }

        Spacer(Modifier.height(24.dp))
        BigSecondaryButton(text = stringResource(R.string.action_back), onClick = onBack)
    }
}
