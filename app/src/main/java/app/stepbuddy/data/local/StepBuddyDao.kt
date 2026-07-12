package app.stepbuddy.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface StepBuddyDao {

    // ---- Guides + steps (read) ----

    @Query("SELECT * FROM guides ORDER BY updatedAt DESC")
    fun observeGuides(): Flow<List<GuideEntity>>

    @Query("SELECT * FROM guides WHERE authorId = :authorId ORDER BY updatedAt DESC")
    fun observeGuidesByAuthor(authorId: String): Flow<List<GuideEntity>>

    @Query("SELECT * FROM guides WHERE id = :guideId")
    fun observeGuide(guideId: String): Flow<GuideEntity?>

    @Query("SELECT * FROM steps WHERE guideId = :guideId ORDER BY step_order ASC")
    fun observeSteps(guideId: String): Flow<List<StepEntity>>

    @Query("SELECT * FROM steps WHERE guideId = :guideId ORDER BY step_order ASC")
    suspend fun getSteps(guideId: String): List<StepEntity>

    @Query("SELECT * FROM guides WHERE id = :guideId")
    suspend fun getGuide(guideId: String): GuideEntity?

    // ---- Guides + steps (write) ----

    @Upsert
    suspend fun upsertGuide(guide: GuideEntity)

    @Upsert
    suspend fun upsertSteps(steps: List<StepEntity>)

    @Query("DELETE FROM steps WHERE guideId = :guideId")
    suspend fun deleteStepsForGuide(guideId: String)

    @Query("DELETE FROM guides WHERE id = :guideId")
    suspend fun deleteGuide(guideId: String)

    /** Replace a guide and all of its steps atomically (used by sync + editor). */
    @Transaction
    suspend fun replaceGuideWithSteps(guide: GuideEntity, steps: List<StepEntity>) {
        upsertGuide(guide)
        deleteStepsForGuide(guide.id)
        upsertSteps(steps)
    }

    /**
     * Remove synced guides that no longer exist upstream, while PRESERVING
     * locally-seeded sample guides (identified by [protectedAuthor]).
     */
    @Query("DELETE FROM guides WHERE authorId != :protectedAuthor AND id NOT IN (:keepIds)")
    suspend fun deleteSyncedGuidesNotIn(keepIds: List<String>, protectedAuthor: String)

    /** Used when the upstream set is empty: clears all non-sample guides. */
    @Query("DELETE FROM guides WHERE authorId != :protectedAuthor")
    suspend fun deleteAllSyncedGuides(protectedAuthor: String)

    // ---- Pairings ----

    @Query("SELECT * FROM pairings WHERE caregiverId = :caregiverId ORDER BY createdAt DESC")
    fun observePairingsByCaregiver(caregiverId: String): Flow<List<PairingEntity>>

    @Query("SELECT * FROM pairings WHERE elderlyId = :elderlyId AND status = 'ACTIVE'")
    fun observeActivePairingsForElderly(elderlyId: String): Flow<List<PairingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPairing(pairing: PairingEntity)

    @Query("DELETE FROM pairings WHERE id = :id")
    suspend fun deletePairing(id: String)
}
