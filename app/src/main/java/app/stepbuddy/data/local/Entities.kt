package app.stepbuddy.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entities backing the local, offline-first cache. After the first sync
 * the elderly device works fully offline from these tables.
 */

@Entity(tableName = "guides")
data class GuideEntity(
    @PrimaryKey val id: String,
    val authorId: String,
    val title: String,
    val iconEmoji: String,
    val languageCode: String,
    val updatedAt: Long,
)

@Entity(
    tableName = "steps",
    foreignKeys = [
        ForeignKey(
            entity = GuideEntity::class,
            parentColumns = ["id"],
            childColumns = ["guideId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("guideId")],
)
data class StepEntity(
    @PrimaryKey val id: String,
    val guideId: String,
    // "order" is a reserved SQL word — store under a safe column name.
    @ColumnInfo(name = "step_order") val order: Int,
    val instructionText: String,
    val imagePath: String?,
    // HighlightRect serialized to JSON by Converters, or null.
    val highlightJson: String?,
    val targetType: String,
    val targetValue: String?,
)

@Entity(tableName = "pairings")
data class PairingEntity(
    @PrimaryKey val id: String,
    val caregiverId: String,
    val elderlyId: String?,
    val code: String,
    val createdAt: Long,
    val status: String,
    val elderlyLabel: String?,
)
