package app.stepbuddy.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.stepbuddy.data.export.GuideExporter
import app.stepbuddy.data.model.Guide
import app.stepbuddy.data.model.UserRole
import app.stepbuddy.data.model.UserSettings
import app.stepbuddy.data.repository.GuideRepository
import app.stepbuddy.data.settings.SettingsRepository
import app.stepbuddy.tts.TtsManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val guides: GuideRepository,
    private val exporter: GuideExporter,
    private val tts: TtsManager,
) : ViewModel() {

    val state: StateFlow<UserSettings> = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    val allGuides: StateFlow<List<Guide>> = guides.observeAllGuides()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setLanguage(code: String) = viewModelScope.launch { settings.setLanguage(code) }
    fun setFontScale(scale: Float) = viewModelScope.launch { settings.setFontScale(scale) }
    fun setSpeechRate(rate: Float) = viewModelScope.launch { settings.setSpeechRate(rate) }
    fun setOverlay(enabled: Boolean) = viewModelScope.launch { settings.setOverlayEnabled(enabled) }
    fun setRole(role: UserRole) = viewModelScope.launch { settings.setRole(role) }

    fun testVoice(text: String) {
        val s = state.value
        tts.speak(text, s.languageCode, s.speechRate)
    }

    /** Returns a shareable Uri for the exported ".stepbuddy" file. */
    fun export(guide: Guide): Uri = exporter.export(guide)

    fun import(uri: Uri, onResult: (Boolean) -> Unit) = viewModelScope.launch {
        exporter.import(uri).fold(
            onSuccess = { guides.cacheLocalGuide(it); onResult(true) },
            onFailure = { onResult(false) },
        )
    }
}
