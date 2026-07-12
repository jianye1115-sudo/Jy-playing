package app.stepbuddy.service

import app.stepbuddy.data.model.Guide
import app.stepbuddy.tts.TtsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The single, app-wide source of truth for an in-progress guide playback.
 *
 * Both the on-screen [ui.elderly.GuidePlaybackScreen] and the persistent
 * notification (via [GuidePlaybackService]) — and the optional overlay bubble —
 * READ [state] and WRITE through the same methods here. Because they all share
 * this one instance, the on-screen buttons and the notification buttons stay
 * perfectly in sync: whoever moves the step, everyone re-renders from [state].
 */
class PlaybackController(private val tts: TtsManager) {

    data class State(
        val guide: Guide,
        val index: Int,
        val speechRate: Float,
    ) {
        val total: Int get() = guide.steps.size
        val step get() = guide.steps[index]
        val isFirst: Boolean get() = index == 0
        val isLast: Boolean get() = index >= total - 1
        val humanStep: Int get() = index + 1
    }

    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state.asStateFlow()

    fun start(guide: Guide, speechRate: Float) {
        if (guide.steps.isEmpty()) return
        _state.value = State(guide, 0, speechRate)
        speakCurrent()
    }

    fun next() = move(+1)
    fun back() = move(-1)

    /** Re-read the current step aloud (the big "Repeat" action). */
    fun repeat() = speakCurrent()

    fun goTo(index: Int) {
        val s = _state.value ?: return
        if (index in 0 until s.total) {
            _state.value = s.copy(index = index)
            speakCurrent()
        }
    }

    fun setSpeechRate(rate: Float) {
        _state.value = _state.value?.copy(speechRate = rate)
    }

    /** End playback entirely (the "Close" action). */
    fun stop() {
        tts.stop()
        _state.value = null
    }

    private fun move(delta: Int) {
        val s = _state.value ?: return
        val target = (s.index + delta).coerceIn(0, s.total - 1)
        if (target != s.index) {
            _state.value = s.copy(index = target)
        }
        speakCurrent()
    }

    private fun speakCurrent() {
        val s = _state.value ?: return
        tts.speak(s.step.instructionText, s.guide.languageCode, s.speechRate)
    }
}
