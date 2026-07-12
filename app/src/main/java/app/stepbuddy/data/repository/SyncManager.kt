package app.stepbuddy.data.repository

import android.util.Log
import app.stepbuddy.data.model.UserRole
import app.stepbuddy.data.remote.FirebaseSync
import app.stepbuddy.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Wires the live Firestore push into the local Room cache for the ELDERLY role.
 *
 * Pipeline:
 *   pairings(elderlyId, ACTIVE)  ->  caregiverIds
 *      -> guides authored by those caregivers (+ their steps)
 *          -> written into Room  ->  the UI (which only reads Room) updates.
 *
 * The caregiver role writes through [GuideRepository.saveGuide] directly, so it
 * needs no background listener here. We carry the role alongside each emission
 * so caching only ever runs for the elderly device (caching an empty list on a
 * caregiver device would wipe its own authored guides).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncManager(
    private val settings: SettingsRepository,
    private val firebase: FirebaseSync,
    private val guides: GuideRepository,
) {
    private var started = false

    /** Idempotently start syncing in the given (application) scope. */
    fun start(scope: CoroutineScope) {
        if (started) return
        started = true

        scope.launch {
            val uid = firebase.ensureSignedIn()
            if (uid == null) {
                Log.i(TAG, "No Firebase session; running from local cache only.")
                return@launch
            }

            settings.settings
                .map { it.role }
                .distinctUntilChanged()
                .flatMapLatest { role ->
                    if (role == UserRole.ELDERLY) {
                        firebase.observePairingsForElderly(uid)
                            .map { pairings -> pairings.map { it.caregiverId }.distinct() }
                            .distinctUntilChanged()
                            .flatMapLatest { authorIds -> firebase.observeGuidesForAuthors(authorIds) }
                            .map { UserRole.ELDERLY to it }
                    } else {
                        flowOf(role to emptyList())
                    }
                }
                .collect { (role, syncedGuides) ->
                    if (role == UserRole.ELDERLY) guides.cacheSyncedGuides(syncedGuides)
                }
        }
    }

    companion object {
        private const val TAG = "SyncManager"
    }
}
