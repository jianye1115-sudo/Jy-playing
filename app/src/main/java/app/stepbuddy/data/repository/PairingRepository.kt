package app.stepbuddy.data.repository

import app.stepbuddy.data.model.Pairing
import app.stepbuddy.data.model.PairingStatus
import app.stepbuddy.data.remote.FirebaseSync
import kotlinx.coroutines.flow.Flow
import kotlin.random.Random

/**
 * Owns pairing creation (caregiver) and claiming (elderly). Codes are 6 random
 * digits; the invite link embeds the same code so both pairing paths converge
 * on one Firestore document keyed by that code.
 */
class PairingRepository(
    private val firebase: FirebaseSync,
) {

    fun observeCaregiverPairings(caregiverId: String): Flow<List<Pairing>> =
        firebase.observePairingsForCaregiver(caregiverId)

    fun observeElderlyPairings(elderlyId: String): Flow<List<Pairing>> =
        firebase.observePairingsForElderly(elderlyId)

    /** Caregiver: mint a new PENDING pairing and return it (with code + link). */
    suspend fun createPairing(caregiverId: String): Result<Pairing> {
        val code = generateCode()
        val pairing = Pairing(
            id = code,
            caregiverId = caregiverId,
            code = code,
            createdAt = System.currentTimeMillis(),
            status = PairingStatus.PENDING,
        )
        return firebase.createPairing(pairing).map { pairing }
    }

    /** Elderly: claim a code (from typing, QR, or a pre-filled invite link). */
    suspend fun claimByCode(code: String, elderlyId: String, label: String?): Result<Pairing> =
        firebase.claimPairing(code.trim(), elderlyId, label)

    suspend fun revoke(code: String): Result<Unit> = firebase.revokePairing(code)

    fun inviteLink(code: String): String = "$INVITE_BASE$code"

    private fun generateCode(): String = Random.nextInt(0, 1_000_000).toString().padStart(6, '0')

    companion object {
        /** Must match the App Link host in AndroidManifest.xml and assetlinks.json. */
        const val INVITE_HOST = "stepbuddy.app"
        const val INVITE_BASE = "https://$INVITE_HOST/pair?code="
    }
}
