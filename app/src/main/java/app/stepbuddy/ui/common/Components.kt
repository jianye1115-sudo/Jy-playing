package app.stepbuddy.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.stepbuddy.R
import app.stepbuddy.data.model.HighlightRect
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import java.io.File

/**
 * Reusable, accessibility-first building blocks. Buttons are tall (>=64dp),
 * text is large, and there is one clear action per button — matching the
 * elderly UX rules.
 */

@Composable
fun BigPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 18.dp),
        modifier = modifier.fillMaxWidth().heightIn(min = 64.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp).padding(end = 8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
    }
}

@Composable
fun BigSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 18.dp),
        modifier = modifier.fillMaxWidth().heightIn(min = 64.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp).padding(end = 8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }
}

/** Confirmation dialog required before any destructive action. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = stringResource(R.string.action_delete),
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(message, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(); onDismiss() },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(confirmLabel, style = MaterialTheme.typography.labelLarge) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}

/**
 * Renders a step's screenshot from any of the supported sources and, if set,
 * draws the caregiver's highlight box on top (coordinates are normalized 0..1).
 */
@Composable
fun StepImage(
    imagePath: String?,
    highlight: HighlightRect?,
    modifier: Modifier = Modifier,
) {
    if (imagePath == null) return
    val context = LocalContext.current

    Box(modifier = modifier) {
        when {
            imagePath.startsWith("sample:") -> {
                val res = when (imagePath.removePrefix("sample:")) {
                    "bank" -> R.drawable.sample_bank
                    "ewallet" -> R.drawable.sample_ewallet
                    else -> R.drawable.sample_bank
                }
                androidx.compose.foundation.Image(
                    painter = painterResource(res),
                    contentDescription = stringResource(R.string.cd_step_screenshot),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            else -> {
                val model = if (imagePath.startsWith("http")) imagePath else File(imagePath)
                AsyncImage(
                    model = ImageRequest.Builder(context).data(model).crossfade(true).build(),
                    contentDescription = stringResource(R.string.cd_step_screenshot),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (highlight != null && highlight.isValid) {
            val outline = MaterialTheme.colorScheme.error
            Canvas(modifier = Modifier.matchParentSize()) {
                val left = highlight.left * size.width
                val top = highlight.top * size.height
                val right = highlight.right * size.width
                val bottom = highlight.bottom * size.height
                drawRoundRect(
                    color = outline,
                    topLeft = androidx.compose.ui.geometry.Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f),
                    style = Stroke(width = 8f),
                )
            }
        }
    }
}

/** A large tappable emoji + label tile used on the home grids. */
@Composable
fun EmojiTitle(emoji: String, modifier: Modifier = Modifier) {
    Text(emoji, style = MaterialTheme.typography.displaySmall, modifier = modifier)
}

// Kept for callers that want a neutral divider color without importing the scheme.
val HairlineColor: Color = Color(0x33000000)
