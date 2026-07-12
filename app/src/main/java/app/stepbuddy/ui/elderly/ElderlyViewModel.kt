package app.stepbuddy.ui.elderly

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.stepbuddy.data.model.Guide
import app.stepbuddy.data.model.UserSettings
import app.stepbuddy.data.repository.GuideRepository
import app.stepbuddy.data.settings.SettingsRepository
import app.stepbuddy.service.GuidePlaybackService
import app.stepbuddy.tts.TtsManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class ElderlyViewModel(
    guideRepository: GuideRepository,
    private val settings: SettingsRepository,
    private val tts: TtsManager,
) : ViewModel() {

    val guides: StateFlow<List<Guide>> = guideRepository.observeAllGuides()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settingsState: StateFlow<UserSettings> = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    /** Kick off guidance for a guide; the foreground service owns playback. */
    fun startGuide(context: Context, guideId: String) {
        GuidePlaybackService.start(context, guideId)
    }

    /** "Read this screen" — speak arbitrary on-screen text in the user's language. */
    fun readAloud(text: String) {
        val s = settingsState.value
        tts.speak(text, s.languageCode, s.speechRate)
    }

    fun stopReading() = tts.stop()
}
