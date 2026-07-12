package app.stepbuddy.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.stepbuddy.data.SampleGuides
import app.stepbuddy.data.model.UserRole
import app.stepbuddy.data.repository.GuideRepository
import app.stepbuddy.data.settings.SettingsRepository
import kotlinx.coroutines.launch

class OnboardingViewModel(
    private val settings: SettingsRepository,
    private val guides: GuideRepository,
) : ViewModel() {

    fun setRole(role: UserRole) = viewModelScope.launch { settings.setRole(role) }

    fun setLanguage(code: String) = viewModelScope.launch { settings.setLanguage(code) }

    /**
     * Finishes onboarding. For the elderly role we seed the two sample guides so
     * the home screen isn't empty before pairing; they persist through sync.
     */
    fun complete(role: UserRole) = viewModelScope.launch {
        if (role == UserRole.ELDERLY) {
            SampleGuides.all().forEach { guides.cacheLocalGuide(it) }
        }
        settings.setOnboarded(true)
    }
}
