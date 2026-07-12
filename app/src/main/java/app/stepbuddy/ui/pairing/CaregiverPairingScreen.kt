package app.stepbuddy.ui.pairing

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.stepbuddy.R
import app.stepbuddy.ui.common.BigPrimaryButton
import app.stepbuddy.ui.common.BigSecondaryButton
import app.stepbuddy.ui.containerViewModel
import app.stepbuddy.util.QrUtils

/**
 * Caregiver pairing screen. Shows a 6-digit code + QR + a shareable invite link
 * (App Link). The elderly device can type the code, scan the QR, or tap the link
 * — all three converge on the same Firestore pairing document.
 */
@Composable
fun CaregiverPairingScreen(onBack: () -> Unit) {
    val vm = containerViewModel { CaregiverPairingViewModel(it.pairingRepository, it.firebaseSync) }
    val current by vm.current.collectAsStateWithLifecycle()
    val pairings by vm.pairings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val code = current?.code
    val link = code?.let { vm.inviteLink(it) }
    val connected = code != null && pairings.any { it.code == code && it.status.name == "ACTIVE" }
    val qr = remember(link) { link?.let { QrUtils.generate(it) } }

    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.pairing_title), style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.pairing_caregiver_desc),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))

        Text(stringResource(R.string.pairing_code_label), style = MaterialTheme.typography.titleMedium)
        Text(
            code ?: "······",
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 8.sp,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))

        qr?.let {
            Image(
                bitmap = it,
                contentDescription = stringResource(R.string.cd_qr_code),
                modifier = Modifier.size(220.dp),
            )
        }
        Spacer(Modifier.height(16.dp))

        Text(
            if (connected) stringResource(R.string.pairing_connected) else stringResource(R.string.pairing_waiting),
            style = MaterialTheme.typography.titleLarge,
            color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        BigPrimaryButton(
            text = stringResource(R.string.pairing_share_link),
            icon = Icons.Filled.Share,
            enabled = link != null,
            onClick = { link?.let { shareLink(context, it) } },
        )
        Spacer(Modifier.height(12.dp))
        BigSecondaryButton(text = stringResource(R.string.pairing_new_code), onClick = { vm.newCode() })
        Spacer(Modifier.height(12.dp))
        BigSecondaryButton(text = stringResource(R.string.action_back), onClick = onBack)
    }
}

private fun shareLink(context: android.content.Context, link: String) {
    val message = "Open StepBuddy with this link to connect: $link"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, message)
    }
    context.startActivity(Intent.createChooser(intent, null))
}
