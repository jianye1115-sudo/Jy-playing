package app.stepbuddy.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.stepbuddy.data.model.UserRole
import app.stepbuddy.data.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Reads/writes the small [UserSettings] object (role, language, font scale,
 * speech rate, overlay toggle, onboarding flag) via Preferences DataStore.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val ROLE = stringPreferencesKey("role")
        val LANGUAGE = stringPreferencesKey("language")
        val FONT_SCALE = floatPreferencesKey("font_scale")
        val SPEECH_RATE = floatPreferencesKey("speech_rate")
        val OVERLAY = booleanPreferencesKey("overlay_enabled")
        val ONBOARDED = booleanPreferencesKey("onboarded")
    }

    val settings: Flow<UserSettings> = context.dataStore.data.map { p ->
        UserSettings(
            role = runCatching { UserRole.valueOf(p[Keys.ROLE] ?: "UNSET") }.getOrDefault(UserRole.UNSET),
            languageCode = p[Keys.LANGUAGE] ?: "en",
            fontScale = p[Keys.FONT_SCALE] ?: 1.3f,
            speechRate = p[Keys.SPEECH_RATE] ?: 0.9f,
            overlayEnabled = p[Keys.OVERLAY] ?: false,
            onboarded = p[Keys.ONBOARDED] ?: false,
        )
    }

    suspend fun setRole(role: UserRole) = edit { it[Keys.ROLE] = role.name }
    suspend fun setLanguage(code: String) = edit { it[Keys.LANGUAGE] = code }
    suspend fun setFontScale(scale: Float) = edit { it[Keys.FONT_SCALE] = scale }
    suspend fun setSpeechRate(rate: Float) = edit { it[Keys.SPEECH_RATE] = rate }
    suspend fun setOverlayEnabled(enabled: Boolean) = edit { it[Keys.OVERLAY] = enabled }
    suspend fun setOnboarded(done: Boolean) = edit { it[Keys.ONBOARDED] = done }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
