package app.stepbuddy.ui.elderly

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.stepbuddy.R
import app.stepbuddy.StepBuddyApp
import app.stepbuddy.data.model.TargetType
import app.stepbuddy.service.PlaybackController
import app.stepbuddy.ui.common.BigPrimaryButton
import app.stepbuddy.ui.common.BigSecondaryButton
import app.stepbuddy.ui.common.StepImage
import app.stepbuddy.util.IntentLauncher

/**
 * Plays a guide one step at a time. It renders whatever the shared
 * [PlaybackController] holds, so the on-screen controls and the persistent
 * notification are always showing the same step.
 *
 * The elderly user reads/hears the instruction, taps "Ready" to open the target
 * app/page, does the real tapping there, then comes back and taps Next.
 */
@Composable
fun GuidePlaybackScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val controller = remember { (context.applicationContext as StepBuddyApp).container.playbackController }
    val state by controller.state.collectAsStateWithLifecycle()

    // Track lifecycle so a momentary null (before the service loads the guide)
    // doesn't exit, but a real Close/Finish does.
    var everActive by remember { mutableStateOf(false) }
    var completed by remember { mutableStateOf(false) }

    LaunchedEffect(state, completed) {
        if (state != null) everActive = true
        else if (everActive && !completed) onExit()
    }

    when {
        completed -> DoneScreen(onExit)
        state == null -> LoadingScreen()
        else -> PlayingScreen(
            state = state!!,
            onRepeat = controller::repeat,
            onBack = controller::back,
            onNext = controller::next,
            onFinish = { completed = true; controller.stop() },
            onClose = { controller.stop() },
            onReady = { IntentLauncher.launchTarget(context, state!!.step) },
        )
    }
}

@Composable
private fun PlayingScreen(
    state: PlaybackController.State,
    onRepeat: () -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit,
    onClose: () -> Unit,
    onReady: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
    ) {
        Text(
            stringResource(R.string.playback_step_of, state.humanStep, state.total),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { state.humanStep.toFloat() / state.total.toFloat() },
            modifier = Modifier.fillMaxWidth().height(10.dp),
        )
        Spacer(Modifier.height(20.dp))

        Text(
            state.step.instructionText,
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(20.dp))

        if (state.step.imagePath != null) {
            StepImage(
                imagePath = state.step.imagePath,
                highlight = state.step.highlight,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
        }

        if (state.step.targetType != TargetType.NONE && state.step.targetValue != null) {
            BigPrimaryButton(
                text = if (state.step.targetType == TargetType.APP)
                    stringResource(R.string.playback_ready_open_app)
                else stringResource(R.string.playback_ready_open_page),
                onClick = onReady,
            )
            Spacer(Modifier.height(20.dp))
        }

        // Repeat is the single most-used action — full width and prominent.
        BigSecondaryButton(
            text = stringResource(R.string.playback_repeat),
            icon = Icons.Filled.Refresh,
            onClick = onRepeat,
        )
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigSecondaryButton(
                text = stringResource(R.string.playback_back),
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                enabled = !state.isFirst,
                onClick = onBack,
                modifier = Modifier.weight(1f),
            )
            if (state.isLast) {
                BigPrimaryButton(
                    text = stringResource(R.string.playback_finish),
                    onClick = onFinish,
                    modifier = Modifier.weight(1f),
                )
            } else {
                BigPrimaryButton(
                    text = stringResource(R.string.playback_next),
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    onClick = onNext,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        BigSecondaryButton(text = stringResource(R.string.playback_close), onClick = onClose)
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.playback_start) + "…", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun DoneScreen(onExit: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🎉", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.playback_done_title), style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.playback_done_desc), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        BigPrimaryButton(stringResource(R.string.action_ok), onClick = onExit)
    }
}
