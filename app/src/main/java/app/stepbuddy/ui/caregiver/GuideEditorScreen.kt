package app.stepbuddy.ui.caregiver

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.stepbuddy.R
import app.stepbuddy.data.model.AppLanguage
import app.stepbuddy.data.model.HighlightRect
import app.stepbuddy.data.model.Step
import app.stepbuddy.data.model.TargetType
import app.stepbuddy.ui.common.BigPrimaryButton
import app.stepbuddy.ui.common.BigSecondaryButton
import app.stepbuddy.ui.common.ConfirmDialog
import app.stepbuddy.service.GuidePlaybackService
import app.stepbuddy.ui.common.StepImage
import app.stepbuddy.ui.containerViewModel
import app.stepbuddy.util.ImageStorage
import java.io.File

/**
 * Guide authoring screen: title/icon/language, then an ordered list of step
 * editors. Each step supports plain-language text, an optional screenshot with a
 * drag-to-draw highlight box, and an optional "Ready" target (URL or app).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideEditorScreen(guideId: String?, onDone: () -> Unit, onPreview: () -> Unit) {
    val vm = containerViewModel {
        GuideEditorViewModel(it.guideRepository, it.settingsRepository, it.firebaseSync)
    }
    LaunchedEffect(Unit) { vm.load(guideId) }

    val draft by vm.draft.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Single image picker shared by all steps; remember which step asked.
    var pickingStepId by remember { mutableStateOf<String?>(null) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        val stepId = pickingStepId
        if (uri != null && stepId != null) {
            ImageStorage.copyToPrivate(context, uri)?.let { vm.setStepImage(stepId, it) }
        }
        pickingStepId = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (guideId == null) stringResource(R.string.caregiver_new_guide) else stringResource(R.string.editor_save)) },
                actions = {
                    TextButton(onClick = { vm.save(onDone) }) {
                        Text(stringResource(R.string.editor_save), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        val guide = draft ?: return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                OutlinedTextField(
                    value = guide.title,
                    onValueChange = vm::updateTitle,
                    label = { Text(stringResource(R.string.editor_guide_title_hint)) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = guide.iconEmoji,
                        onValueChange = { vm.updateIcon(it.take(2)) },
                        label = { Text(stringResource(R.string.editor_guide_icon_hint)) },
                        singleLine = true,
                        modifier = Modifier.width(120.dp),
                    )
                }
            }
            item {
                Text(stringResource(R.string.editor_guide_language), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = guide.languageCode == lang.code,
                            onClick = { vm.updateLanguage(lang.code) },
                            label = { Text(lang.nativeName) },
                        )
                    }
                }
            }
            item {
                Card(colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Text(
                        stringResource(R.string.step_sensitive_warning),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            if (guide.steps.isEmpty()) {
                item { Text(stringResource(R.string.editor_no_steps), style = MaterialTheme.typography.bodyLarge) }
            } else {
                items(guide.steps, key = { it.id }) { step ->
                    StepEditorCard(
                        step = step,
                        index = step.order,
                        total = guide.steps.size,
                        onChange = vm::updateStep,
                        onDelete = { vm.deleteStep(step.id) },
                        onDuplicate = { vm.duplicateStep(step.id) },
                        onMoveUp = { vm.moveStep(step.id, -1) },
                        onMoveDown = { vm.moveStep(step.id, +1) },
                        onPickImage = { pickingStepId = step.id; imagePicker.launch("image/*") },
                        onRemoveImage = { vm.setStepImage(step.id, null) },
                        onSetHighlight = { vm.setStepHighlight(step.id, it) },
                        onSetTarget = { type, value -> vm.setStepTarget(step.id, type, value) },
                    )
                }
            }

            item {
                BigSecondaryButton(
                    text = stringResource(R.string.editor_add_step),
                    icon = Icons.Filled.Add,
                    onClick = { vm.addStep() },
                )
            }
            item {
                // Preview: save, then play the guide through the same playback
                // path the elderly device uses (on-screen + spoken + notification).
                BigSecondaryButton(
                    text = stringResource(R.string.editor_preview),
                    enabled = guide.steps.isNotEmpty(),
                    onClick = { vm.save { GuidePlaybackService.start(context, guide.id); onPreview() } },
                )
            }
            item {
                BigPrimaryButton(text = stringResource(R.string.editor_save), onClick = { vm.save(onDone) })
            }
        }
    }
}

@Composable
private fun StepEditorCard(
    step: Step,
    index: Int,
    total: Int,
    onChange: (Step) -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onPickImage: () -> Unit,
    onRemoveImage: () -> Unit,
    onSetHighlight: (HighlightRect?) -> Unit,
    onSetTarget: (TargetType, String?) -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var editingHighlight by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Step ${index + 1}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onMoveUp, enabled = index > 0) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = stringResource(R.string.editor_move_up))
                }
                IconButton(onClick = onMoveDown, enabled = index < total - 1) {
                    Icon(Icons.Filled.ArrowDownward, contentDescription = stringResource(R.string.editor_move_down))
                }
                IconButton(onClick = onDuplicate) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.editor_duplicate))
                }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.editor_delete), tint = MaterialTheme.colorScheme.error)
                }
            }

            OutlinedTextField(
                value = step.instructionText,
                onValueChange = { onChange(step.copy(instructionText = it)) },
                label = { Text(stringResource(R.string.step_instruction_hint)) },
                textStyle = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            // Screenshot + highlight
            if (step.imagePath == null) {
                BigSecondaryButton(text = stringResource(R.string.step_add_screenshot), onClick = onPickImage)
            } else {
                if (editingHighlight) {
                    HighlightEditor(
                        imagePath = step.imagePath,
                        initial = step.highlight,
                        onDone = { rect -> onSetHighlight(rect); editingHighlight = false },
                    )
                } else {
                    StepImage(imagePath = step.imagePath, highlight = step.highlight, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { editingHighlight = !editingHighlight }) {
                        Text(stringResource(R.string.step_highlight))
                    }
                    TextButton(onClick = { onRemoveImage(); editingHighlight = false }) {
                        Text(stringResource(R.string.step_remove_screenshot), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // Target
            Text(stringResource(R.string.step_target_label), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = step.targetType == TargetType.NONE,
                    onClick = { onSetTarget(TargetType.NONE, null) },
                    label = { Text(stringResource(R.string.step_target_none)) },
                )
                FilterChip(
                    selected = step.targetType == TargetType.URL,
                    onClick = { onSetTarget(TargetType.URL, step.targetValue) },
                    label = { Text(stringResource(R.string.step_target_url)) },
                )
                FilterChip(
                    selected = step.targetType == TargetType.APP,
                    onClick = { onSetTarget(TargetType.APP, step.targetValue) },
                    label = { Text(stringResource(R.string.step_target_app)) },
                )
            }
            if (step.targetType != TargetType.NONE) {
                OutlinedTextField(
                    value = step.targetValue.orEmpty(),
                    onValueChange = { onSetTarget(step.targetType, it) },
                    label = {
                        Text(
                            if (step.targetType == TargetType.URL) stringResource(R.string.step_target_url_hint)
                            else stringResource(R.string.step_target_app_hint),
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.confirm_delete_step_title),
            message = "",
            onConfirm = onDelete,
            onDismiss = { confirmDelete = false },
        )
    }
}

/**
 * Drag on the screenshot to draw the highlight box. Coordinates are captured as
 * normalized fractions (0..1) so they render correctly at any size on the
 * elderly device.
 */
@Composable
private fun HighlightEditor(imagePath: String, initial: HighlightRect?, onDone: (HighlightRect?) -> Unit) {
    var start by remember { mutableStateOf<Offset?>(null) }
    var currentRect by remember { mutableStateOf(initial) }
    var boxSize by remember { mutableStateOf(Size.Zero) }
    val outline = MaterialTheme.colorScheme.error

    Column {
        Text("Drag over the button to highlight it.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.fillMaxWidth()
                .pointerInput(imagePath) {
                    detectDragGestures(
                        onDragStart = { start = it },
                        onDrag = { change, _ ->
                            val s = start ?: return@detectDragGestures
                            val e = change.position
                            if (boxSize.width > 0 && boxSize.height > 0) {
                                currentRect = HighlightRect(
                                    left = (minOf(s.x, e.x) / boxSize.width).coerceIn(0f, 1f),
                                    top = (minOf(s.y, e.y) / boxSize.height).coerceIn(0f, 1f),
                                    right = (maxOf(s.x, e.x) / boxSize.width).coerceIn(0f, 1f),
                                    bottom = (maxOf(s.y, e.y) / boxSize.height).coerceIn(0f, 1f),
                                )
                            }
                        },
                    )
                },
        ) {
            // Reuse StepImage renderer, then draw the live rect on top.
            val context = LocalContext.current
            when {
                imagePath.startsWith("sample:") -> {
                    val res = if (imagePath.endsWith("ewallet")) R.drawable.sample_ewallet else R.drawable.sample_bank
                    Image(painterResource(res), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth())
                }
                else -> {
                    val model = if (imagePath.startsWith("http")) imagePath else File(imagePath)
                    coil.compose.AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth())
                }
            }
            Canvas(Modifier.matchParentSize()) {
                boxSize = size
                currentRect?.takeIf { it.isValid }?.let { r ->
                    drawRoundRect(
                        color = outline,
                        topLeft = Offset(r.left * size.width, r.top * size.height),
                        size = Size((r.right - r.left) * size.width, (r.bottom - r.top) * size.height),
                        cornerRadius = CornerRadius(16f, 16f),
                        style = Stroke(width = 8f),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onDone(currentRect) }) { Text(stringResource(R.string.action_save)) }
            TextButton(onClick = { currentRect = null; onDone(null) }) { Text(stringResource(R.string.action_cancel)) }
        }
    }
}
