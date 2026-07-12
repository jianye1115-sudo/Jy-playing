package app.stepbuddy.data.repository

import app.stepbuddy.data.local.GuideEntity
import app.stepbuddy.data.local.StepBuddyDao
import app.stepbuddy.data.local.toDomain
import app.stepbuddy.data.local.toEntity
import app.stepbuddy.data.model.Guide
import app.stepbuddy.data.remote.FirebaseSync
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Single source of truth for guides. The UI always reads from Room (fast +
 * offline); writes go to Room immediately and are mirrored to Firestore so they
 * push to linked devices. Incoming remote changes are written to Room by
 * [SyncManager].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GuideRepository(
    private val dao: StepBuddyDao,
    private val firebase: FirebaseSync,
) {

    /** Every cached guide with its ordered steps (used by the elderly home). */
    fun observeAllGuides(): Flow<List<Guide>> =
        dao.observeGuides().flatMapLatest { combineWithSteps(it) }

    /** Guides authored by [authorId] with steps (used by the caregiver home). */
    fun observeGuidesByAuthor(authorId: String): Flow<List<Guide>> =
        dao.observeGuidesByAuthor(authorId).flatMapLatest { combineWithSteps(it) }

    /** One guide with its steps, kept live for the editor / playback screens. */
    fun observeGuide(guideId: String): Flow<Guide?> =
        combine(dao.observeGuide(guideId), dao.observeSteps(guideId)) { guide, steps ->
            guide?.toDomain(steps.map { it.toDomain() })
        }

    suspend fun getGuide(guideId: String): Guide? {
        val guide = dao.getGuide(guideId) ?: return null
        return guide.toDomain(dao.getSteps(guideId).map { it.toDomain() })
    }

    /** Caregiver save: persist locally, then push to Firestore for linked phones. */
    suspend fun saveGuide(guide: Guide): Result<Unit> {
        dao.replaceGuideWithSteps(guide.toEntity(), guide.steps.map { it.toEntity() })
        return firebase.pushGuide(guide)
    }

    suspend fun deleteGuide(guide: Guide): Result<Unit> {
        dao.deleteGuide(guide.id)
        return firebase.deleteGuide(guide.id)
    }

    /** Called by [SyncManager] when Firestore pushes new/updated guides. */
    suspend fun cacheSyncedGuides(guides: List<Guide>) {
        guides.forEach { g ->
            dao.replaceGuideWithSteps(g.toEntity(), g.steps.map { it.toEntity() })
        }
        // Drop synced guides removed upstream, but keep the local sample guides.
        val keepIds = guides.map { it.id }
        if (keepIds.isEmpty()) {
            dao.deleteAllSyncedGuides(SAMPLE_AUTHOR_ID)
        } else {
            dao.deleteSyncedGuidesNotIn(keepIds, SAMPLE_AUTHOR_ID)
        }
    }

    /** Local-only insert used for the two pre-loaded sample guides. */
    suspend fun cacheLocalGuide(guide: Guide) {
        dao.replaceGuideWithSteps(guide.toEntity(), guide.steps.map { it.toEntity() })
    }

    /** Join each guide entity with its live steps into a single list flow. */
    private fun combineWithSteps(guides: List<GuideEntity>): Flow<List<Guide>> {
        if (guides.isEmpty()) return flowOf(emptyList())
        val perGuide = guides.map { g ->
            dao.observeSteps(g.id).map { steps -> g.toDomain(steps.map { it.toDomain() }) }
        }
        return combine(perGuide) { it.toList() }
    }

    companion object {
        /** Author id used for the pre-loaded sample guides so sync never drops them. */
        const val SAMPLE_AUTHOR_ID = "sample-local"
    }
}
