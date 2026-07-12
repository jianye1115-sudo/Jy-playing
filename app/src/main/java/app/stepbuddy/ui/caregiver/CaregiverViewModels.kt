package app.stepbuddy.ui.caregiver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.stepbuddy.data.model.Guide
import app.stepbuddy.data.model.HighlightRect
import app.stepbuddy.data.model.Pairing
import app.stepbuddy.data.model.Step
import app.stepbuddy.data.model.TargetType
import app.stepbuddy.data.remote.FirebaseSync
import app.stepbuddy.data.repository.GuideRepository
import app.stepbuddy.data.repository.PairingRepository
import app.stepbuddy.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

// --------------------------------------------------------------------- Home

class CaregiverHomeViewModel(
    private val guides: GuideRepository,
    private val firebase: FirebaseSync,
) : ViewModel() {

    // Resolve the caregiver's stable author id (Firebase uid), then stream their
    // guides from Room. Falls back to a local id if Firebase is unreachable.
    val myGuides: StateFlow<List<Guide>> = flow {
        val id = firebase.ensureSignedIn() ?: firebase.uid ?: "local-caregiver"
        emitAll(guides.observeGuidesByAuthor(id))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(guide: Guide) = viewModelScope.launch { guides.deleteGuide(guide) }

    /** Duplicate a guide (and its steps) with fresh ids. */
    fun duplicate(guide: Guide) = viewModelScope.launch {
        val newId = UUID.randomUUID().toString()
        val copy = guide.copy(
            id = newId,
            title = guide.title + " (copy)",
            updatedAt = System.currentTimeMillis(),
            steps = guide.steps.map { it.copy(id = UUID.randomUUID().toString(), guideId = newId) },
        )
        guides.saveGuide(copy)
    }
}

// ------------------------------------------------------------------- Editor

class GuideEditorViewModel(
    private val guides: GuideRepository,
    private val settings: SettingsRepository,
    private val firebase: FirebaseSync,
) : ViewModel() {

    private val _draft = MutableStateFlow<Guide?>(null)
    val draft: StateFlow<Guide?> = _draft.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    /** Load an existing guide, or start a blank one when [guideId] is null. */
    fun load(guideId: String?) = viewModelScope.launch {
        if (_draft.value != null) return@launch
        _draft.value = if (guideId != null) {
            guides.getGuide(guideId)
        } else {
            val authorId = firebase.ensureSignedIn() ?: firebase.uid ?: "local-caregiver"
            val lang = settings.settings.first().languageCode
            val id = UUID.randomUUID().toString()
            Guide(id = id, authorId = authorId, title = "", iconEmoji = "📘", languageCode = lang, updatedAt = System.currentTimeMillis())
        }
    }

    fun updateTitle(title: String) = edit { it.copy(title = title) }
    fun updateIcon(emoji: String) = edit { it.copy(iconEmoji = emoji) }
    fun updateLanguage(code: String) = edit { it.copy(languageCode = code) }

    fun addStep() = edit { guide ->
        val step = Step(
            id = UUID.randomUUID().toString(),
            guideId = guide.id,
            order = guide.steps.size,
            instructionText = "",
        )
        guide.copy(steps = guide.steps + step)
    }

    fun updateStep(updated: Step) = edit { guide ->
        guide.copy(steps = guide.steps.map { if (it.id == updated.id) updated else it })
    }

    fun deleteStep(stepId: String) = edit { guide ->
        guide.copy(steps = guide.steps.filterNot { it.id == stepId }.reindex())
    }

    fun duplicateStep(stepId: String) = edit { guide ->
        val idx = guide.steps.indexOfFirst { it.id == stepId }
        if (idx < 0) return@edit guide
        val copy = guide.steps[idx].copy(id = UUID.randomUUID().toString())
        val list = guide.steps.toMutableList().apply { add(idx + 1, copy) }
        guide.copy(steps = list.reindex())
    }

    fun moveStep(stepId: String, delta: Int) = edit { guide ->
        val list = guide.steps.toMutableList()
        val idx = list.indexOfFirst { it.id == stepId }
        val target = idx + delta
        if (idx < 0 || target !in list.indices) return@edit guide
        list.add(target, list.removeAt(idx))
        guide.copy(steps = list.reindex())
    }

    fun setStepImage(stepId: String, path: String?) = updateStepField(stepId) { it.copy(imagePath = path, highlight = if (path == null) null else it.highlight) }
    fun setStepHighlight(stepId: String, rect: HighlightRect?) = updateStepField(stepId) { it.copy(highlight = rect) }
    fun setStepTarget(stepId: String, type: TargetType, value: String?) =
        updateStepField(stepId) { it.copy(targetType = type, targetValue = value) }

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        val guide = _draft.value ?: return@launch
        _saving.value = true
        val finalized = guide.copy(updatedAt = System.currentTimeMillis(), steps = guide.steps.reindex())
        guides.saveGuide(finalized)
        _saving.value = false
        onDone()
    }

    private fun updateStepField(stepId: String, transform: (Step) -> Step) = edit { guide ->
        guide.copy(steps = guide.steps.map { if (it.id == stepId) transform(it) else it })
    }

    private fun edit(transform: (Guide) -> Guide) {
        _draft.value = _draft.value?.let(transform)
    }

    private fun List<Step>.reindex(): List<Step> = mapIndexed { i, s -> s.copy(order = i) }
}

// ------------------------------------------------------------------ Devices

class CaregiverDevicesViewModel(
    private val pairingRepo: PairingRepository,
    private val firebase: FirebaseSync,
) : ViewModel() {

    private val _pairings = MutableStateFlow<List<Pairing>>(emptyList())
    val pairings: StateFlow<List<Pairing>> = _pairings.asStateFlow()

    init {
        viewModelScope.launch {
            val uid = firebase.ensureSignedIn() ?: firebase.uid ?: return@launch
            pairingRepo.observeCaregiverPairings(uid).collect { _pairings.value = it }
        }
    }

    fun unlink(pairing: Pairing) = viewModelScope.launch { pairingRepo.revoke(pairing.code) }
}
