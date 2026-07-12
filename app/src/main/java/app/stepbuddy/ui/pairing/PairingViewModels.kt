package app.stepbuddy.ui.pairing

import android.os.Build
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.stepbuddy.R
import app.stepbuddy.data.model.Pairing
import app.stepbuddy.data.model.UserRole
import app.stepbuddy.data.remote.FirebaseSync
import app.stepbuddy.data.remote.PairingError
import app.stepbuddy.data.remote.PairingException
import app.stepbuddy.data.repository.PairingRepository
import app.stepbuddy.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- Elderly side

sealed interface ConnectState {
    data object Idle : ConnectState
    data object Connecting : ConnectState
    data object Success : ConnectState
    data class Error(@StringRes val messageRes: Int) : ConnectState
}

class ElderlyPairingViewModel(
    private val pairing: PairingRepository,
    private val firebase: FirebaseSync,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ConnectState>(ConnectState.Idle)
    val state: StateFlow<ConnectState> = _state.asStateFlow()

    /** One-tap connect: sign in anonymously, then claim the code. */
    fun connect(code: String) {
        val clean = code.filter { it.isDigit() }
        if (clean.length != 6) {
            _state.value = ConnectState.Error(R.string.pairing_error_not_found)
            return
        }
        _state.value = ConnectState.Connecting
        viewModelScope.launch {
            // If someone opened an invite link before choosing a role, make this
            // device the elderly one — that is the only role that claims codes.
            settings.setRole(UserRole.ELDERLY)

            val uid = firebase.ensureSignedIn()
            if (uid == null) {
                _state.value = ConnectState.Error(R.string.pairing_error_network)
                return@launch
            }
            val label = "${Build.MANUFACTURER} ${Build.MODEL}"
            pairing.claimByCode(clean, uid, label).fold(
                onSuccess = {
                    settings.setOnboarded(true) // reachable via deep link pre-onboarding
                    _state.value = ConnectState.Success
                },
                onFailure = { _state.value = ConnectState.Error(it.toMessageRes()) },
            )
        }
    }

    fun reset() { _state.value = ConnectState.Idle }

    private fun Throwable.toMessageRes(): Int = when ((this as? PairingException)?.error) {
        PairingError.NOT_FOUND -> R.string.pairing_error_not_found
        PairingError.ALREADY_USED -> R.string.pairing_error_used
        else -> R.string.pairing_error_network
    }
}

// -------------------------------------------------------------- Caregiver side

class CaregiverPairingViewModel(
    private val pairingRepo: PairingRepository,
    private val firebase: FirebaseSync,
) : ViewModel() {

    private val _current = MutableStateFlow<Pairing?>(null)
    val current: StateFlow<Pairing?> = _current.asStateFlow()

    private val _pairings = MutableStateFlow<List<Pairing>>(emptyList())
    val pairings: StateFlow<List<Pairing>> = _pairings.asStateFlow()

    private var uid: String? = null

    init {
        viewModelScope.launch {
            uid = firebase.ensureSignedIn()
            uid?.let { id ->
                // Keep the caregiver's pairing list live (shows "connected!").
                launch { pairingRepo.observeCaregiverPairings(id).collect { _pairings.value = it } }
                // Mint a code as soon as the screen opens.
                if (_current.value == null) newCode()
            }
        }
    }

    fun newCode() {
        val id = uid ?: return
        viewModelScope.launch {
            pairingRepo.createPairing(id).onSuccess { _current.value = it }
        }
    }

    fun inviteLink(code: String): String = pairingRepo.inviteLink(code)

    /** Has the code currently shown been claimed by an elderly device? */
    fun isCurrentConnected(): Boolean {
        val code = _current.value?.code ?: return false
        return _pairings.value.any { it.code == code && it.status.name == "ACTIVE" }
    }
}
