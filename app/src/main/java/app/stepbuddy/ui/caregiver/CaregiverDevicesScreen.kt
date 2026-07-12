package app.stepbuddy.ui.caregiver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.stepbuddy.R
import app.stepbuddy.data.model.Pairing
import app.stepbuddy.data.model.PairingStatus
import app.stepbuddy.ui.common.BigPrimaryButton
import app.stepbuddy.ui.common.ConfirmDialog
import app.stepbuddy.ui.containerViewModel

/** Caregiver: view and unlink the elderly phones connected to this account. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaregiverDevicesScreen(onAddDevice: () -> Unit, onBack: () -> Unit) {
    val vm = containerViewModel { CaregiverDevicesViewModel(it.pairingRepository, it.firebaseSync) }
    val pairings by vm.pairings.collectAsStateWithLifecycle()
    val linked = pairings.filter { it.status == PairingStatus.ACTIVE }
    var pendingUnlink by remember { mutableStateOf<Pairing?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.devices_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            BigPrimaryButton(
                text = stringResource(R.string.devices_add),
                icon = Icons.Filled.Add,
                onClick = onAddDevice,
            )
            Spacer(Modifier.height(16.dp))

            if (linked.isEmpty()) {
                Text(stringResource(R.string.devices_none), style = MaterialTheme.typography.titleMedium)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(linked, key = { it.id }) { pairing ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        pairing.elderlyLabel ?: "Phone",
                                        style = MaterialTheme.typography.titleLarge,
                                    )
                                    Text(
                                        "Code ${pairing.code}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = { pendingUnlink = pairing }) {
                                    Text(stringResource(R.string.devices_unlink), color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingUnlink?.let { pairing ->
        ConfirmDialog(
            title = stringResource(R.string.confirm_unlink_title),
            message = stringResource(R.string.confirm_unlink_desc),
            confirmLabel = stringResource(R.string.devices_unlink),
            onConfirm = { vm.unlink(pairing) },
            onDismiss = { pendingUnlink = null },
        )
    }
}
