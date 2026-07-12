package app.stepbuddy.data.remote

import android.net.Uri
import android.util.Log
import app.stepbuddy.data.model.Guide
import app.stepbuddy.data.model.Pairing
import app.stepbuddy.data.model.PairingStatus
import app.stepbuddy.data.model.Step
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File

/**
 * The single gateway to Firebase: Anonymous Auth, Firestore (guides/steps/
 * pairings) and Storage (screenshots).
 *
 * SYNC MODEL
 *  - Every device signs in anonymously and gets a stable [uid].
 *  - CAREGIVER creates a `pairings/{code}` document (status PENDING) and pushes
 *    guides under `guides/{guideId}` with a `steps` subcollection.
 *  - ELDERLY claims a pairing by code (transaction sets elderlyId + ACTIVE),
 *    then listens to guides whose `authorId` is one of its linked caregivers.
 *    Firestore snapshot listeners give near-instant push; the repository mirrors
 *    everything into Room so the device also works fully offline.
 *
 * Access control is enforced by Firestore Security Rules (see docs/SETUP.md):
 * a guide is only readable by devices with an ACTIVE pairing to its author.
 *
 * All calls degrade gracefully: if Firebase isn't reachable (e.g. the sample
 * google-services.json is still in place) errors are caught and surfaced as
 * failures rather than crashes, and the cached data keeps working.
 */
class FirebaseSync {

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    private val storage: FirebaseStorage get() = FirebaseStorage.getInstance()

    val uid: String? get() = auth.currentUser?.uid

    /** Ensures an anonymous session exists; returns the uid or null on failure. */
    suspend fun ensureSignedIn(): String? = try {
        auth.currentUser?.uid ?: auth.signInAnonymously().await().user?.uid
    } catch (e: Exception) {
        Log.w(TAG, "Anonymous sign-in failed (offline or unconfigured Firebase): ${e.message}")
        null
    }

    // ---------------------------------------------------------------- Pairing

    /** Caregiver: publish a fresh PENDING pairing keyed by its 6-digit code. */
    suspend fun createPairing(pairing: Pairing): Result<Unit> = runCatching {
        db.collection(PAIRINGS).document(pairing.code)
            .set(pairing.toDocMap()).await()
    }

    /**
     * Elderly: atomically claim a PENDING pairing by [code]. Runs in a
     * transaction so two devices can't claim the same code.
     */
    suspend fun claimPairing(code: String, elderlyId: String, label: String?): Result<Pairing> =
        runCatching {
            val ref = db.collection(PAIRINGS).document(code)
            db.runTransaction { txn ->
                val snap = txn.get(ref)
                if (!snap.exists()) throw PairingException(PairingError.NOT_FOUND)
                val doc = snap.toObject(PairingDoc::class.java) ?: throw PairingException(PairingError.NOT_FOUND)
                val status = runCatching { PairingStatus.valueOf(doc.status) }.getOrDefault(PairingStatus.PENDING)
                // Idempotent: if THIS device already claimed it, treat as success.
                if (status == PairingStatus.ACTIVE && doc.elderlyId != elderlyId) {
                    throw PairingException(PairingError.ALREADY_USED)
                }
                txn.update(
                    ref,
                    mapOf(
                        "elderlyId" to elderlyId,
                        "elderlyLabel" to label,
                        "status" to PairingStatus.ACTIVE.name,
                    ),
                )
                // Write the membership link that Firestore Security Rules use to
                // grant this elderly device read access to the caregiver's guides.
                txn.set(
                    db.collection(LINKS).document(linkId(doc.caregiverId, elderlyId)),
                    mapOf("caregiverId" to doc.caregiverId, "elderlyId" to elderlyId, "createdAt" to System.currentTimeMillis()),
                )
                doc.copy(id = code, elderlyId = elderlyId, elderlyLabel = label, status = PairingStatus.ACTIVE.name)
                    .toDomain()
            }.await()
        }

    suspend fun revokePairing(code: String): Result<Unit> = runCatching {
        val ref = db.collection(PAIRINGS).document(code)
        val doc = ref.get().await().toObject(PairingDoc::class.java)
        ref.update("status", PairingStatus.REVOKED.name).await()
        // Remove the membership link so the elderly device loses read access.
        doc?.elderlyId?.let { elderlyId ->
            db.collection(LINKS).document(linkId(doc.caregiverId, elderlyId)).delete().await()
        }
    }

    /** Caregiver: live list of pairings this account created. */
    fun observePairingsForCaregiver(caregiverId: String): Flow<List<Pairing>> = callbackFlow {
        val reg = db.collection(PAIRINGS)
            .whereEqualTo("caregiverId", caregiverId)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                trySend(snap.toObjects(PairingDoc::class.java).map { it.toDomain() })
            }
        awaitClose { reg.remove() }
    }

    /** Elderly: live list of ACTIVE pairings that link this device to caregivers. */
    fun observePairingsForElderly(elderlyId: String): Flow<List<Pairing>> = callbackFlow {
        val reg = db.collection(PAIRINGS)
            .whereEqualTo("elderlyId", elderlyId)
            .whereEqualTo("status", PairingStatus.ACTIVE.name)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                trySend(snap.toObjects(PairingDoc::class.java).map { it.toDomain() })
            }
        awaitClose { reg.remove() }
    }

    // ----------------------------------------------------------------- Guides

    /**
     * Elderly: observe every guide authored by any of [authorIds], each with
     * its ordered steps. Emits a fresh list whenever anything upstream changes.
     */
    fun observeGuidesForAuthors(authorIds: List<String>): Flow<List<Guide>> = callbackFlow {
        if (authorIds.isEmpty()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        // Firestore `whereIn` allows up to 30 values; caregiver counts are tiny.
        val reg = db.collection(GUIDES)
            .whereIn("authorId", authorIds.take(30))
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                val docs = snap.toObjects(GuideDoc::class.java)
                // Fetch each guide's steps, then emit the assembled list.
                launch {
                    val guides = docs.map { g -> g.toDomain(fetchSteps(g.id)) }
                        .sortedByDescending { it.updatedAt }
                    trySend(guides)
                }
            }
        awaitClose { reg.remove() }
    }

    private suspend fun fetchSteps(guideId: String): List<Step> = runCatching {
        db.collection(GUIDES).document(guideId).collection(STEPS)
            .orderBy("order")
            .get().await()
            .toObjects(StepDoc::class.java)
            .map { it.toDomain() }
    }.getOrDefault(emptyList())

    /**
     * Caregiver: push a guide and its steps. Local screenshot files are uploaded
     * to Storage first and replaced with their download URL. Steps removed since
     * the last push are deleted from the subcollection.
     */
    suspend fun pushGuide(guide: Guide): Result<Unit> = runCatching {
        val guideRef = db.collection(GUIDES).document(guide.id)
        guideRef.set(guide.toDoc().toMap()).await()

        val stepsRef = guideRef.collection(STEPS)
        // Delete steps that no longer exist.
        val existingIds = stepsRef.get().await().documents.map { it.id }.toSet()
        val currentIds = guide.steps.map { it.id }.toSet()
        (existingIds - currentIds).forEach { stepsRef.document(it).delete().await() }

        // Upload images + write steps.
        for (step in guide.steps) {
            val remoteUrl = uploadImageIfLocal(guide.id, step)
            stepsRef.document(step.id).set(step.toDoc(remoteUrl).toMap()).await()
        }
    }

    suspend fun deleteGuide(guideId: String): Result<Unit> = runCatching {
        val guideRef = db.collection(GUIDES).document(guideId)
        guideRef.collection(STEPS).get().await().documents.forEach { it.reference.delete().await() }
        guideRef.delete().await()
    }

    /**
     * Uploads a step's screenshot to Storage if [Step.imagePath] points at a
     * local file, returning the public download URL. Remote URLs and bundled
     * `sample:` placeholders are passed through unchanged.
     */
    private suspend fun uploadImageIfLocal(guideId: String, step: Step): String? {
        val path = step.imagePath ?: return null
        if (path.startsWith("http") || path.startsWith("sample:")) return path
        val file = File(path)
        if (!file.exists()) return null
        return runCatching {
            val ref = storage.reference.child("screenshots/$guideId/${step.id}.jpg")
            ref.putFile(Uri.fromFile(file)).await()
            ref.downloadUrl.await().toString()
        }.getOrNull()
    }

    // --------------------------------------------------------------- Mapping

    private fun Pairing.toDocMap(): Map<String, Any?> = mapOf(
        "id" to code,
        "caregiverId" to caregiverId,
        "elderlyId" to elderlyId,
        "code" to code,
        "createdAt" to createdAt,
        "status" to status.name,
        "elderlyLabel" to elderlyLabel,
    )

    private fun GuideDoc.toMap(): Map<String, Any?> = mapOf(
        "id" to id, "authorId" to authorId, "title" to title, "iconEmoji" to iconEmoji,
        "languageCode" to languageCode, "updatedAt" to updatedAt,
    )

    private fun StepDoc.toMap(): Map<String, Any?> = mapOf(
        "id" to id, "guideId" to guideId, "order" to order,
        "instructionText" to instructionText, "imageUrl" to imageUrl,
        "highlight" to highlight, "targetType" to targetType, "targetValue" to targetValue,
    )

    /** Firebase uids contain no '_', so it is a safe delimiter for link ids. */
    private fun linkId(caregiverId: String, elderlyId: String) = "${caregiverId}_$elderlyId"

    companion object {
        private const val TAG = "FirebaseSync"
        private const val GUIDES = "guides"
        private const val STEPS = "steps"
        private const val PAIRINGS = "pairings"
        private const val LINKS = "links"
    }
}

/** Reasons claiming a pairing can fail, mapped to friendly elderly-facing text. */
enum class PairingError { NOT_FOUND, ALREADY_USED, NETWORK }

class PairingException(val error: PairingError) : Exception(error.name)
