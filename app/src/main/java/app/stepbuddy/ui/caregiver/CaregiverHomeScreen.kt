package app.stepbuddy.ui.caregiver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.stepbuddy.R
import app.stepbuddy.data.model.Guide
import app.stepbuddy.ui.common.ConfirmDialog
import app.stepbuddy.ui.containerViewModel

/**
 * Caregiver home: list of authored guides with edit / duplicate / delete, plus
 * entries to link devices and open settings. Changes push to linked phones
 * automatically once saved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaregiverHomeScreen(
    onNewGuide: () -> Unit,
    onEditGuide: (String) -> Unit,
    onManageDevices: () -> Unit,
    onSettings: () -> Unit,
) {
    val vm = containerViewModel { CaregiverHomeViewModel(it.guideRepository, it.firebaseSync) }
    val guides by vm.myGuides.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<Guide?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.caregiver_home_title), style = MaterialTheme.typography.headlineMedium) },
                actions = {
                    IconButton(onClick = onManageDevices) {
                        Icon(Icons.Filled.Devices, contentDescription = stringResource(R.string.caregiver_manage_devices))
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(stringResource(R.string.caregiver_new_guide)) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = onNewGuide,
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(
                stringResource(R.string.caregiver_push_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            if (guides.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.caregiver_no_guides),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(guides, key = { it.id }) { guide ->
                        GuideRow(
                            guide = guide,
                            onEdit = { onEditGuide(guide.id) },
                            onDuplicate = { vm.duplicate(guide) },
                            onDelete = { pendingDelete = guide },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { guide ->
        ConfirmDialog(
            title = stringResource(R.string.confirm_delete_guide_title),
            message = stringResource(R.string.confirm_delete_guide_desc),
            onConfirm = { vm.delete(guide) },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun GuideRow(guide: Guide, onEdit: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit) {
    Card(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(guide.iconEmoji, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(guide.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleLarge)
                Text(
                    "${guide.steps.size} steps · ${guide.languageCode.uppercase()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDuplicate) {
                Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.editor_duplicate))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.editor_delete), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
