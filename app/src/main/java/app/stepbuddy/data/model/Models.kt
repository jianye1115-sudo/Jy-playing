package app.stepbuddy.data.model

import kotlinx.serialization.Serializable

/**
 * Domain models shared across the whole app (UI, Room cache, Firestore sync
 * and .stepbuddy export). These are plain, immutable data classes with no
 * Android or Firebase dependency so they stay easy to test and reuse.
 */

/** Which experience the person on THIS device gets. Chosen once at onboarding. */
enum class UserRole { CAREGIVER, ELDERLY, UNSET }

/** What the "Ready" button does for a step. */
enum class TargetType { NONE, URL, APP }

/** Lifecycle of a pairing between a caregiver and an elderly device. */
enum class PairingStatus { PENDING, ACTIVE, REVOKED }

/**
 * A highlight box drawn on a step's screenshot, stored in NORMALIZED
 * coordinates (0f..1f) so it renders correctly at any image size.
 */
@Serializable
data class HighlightRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val isValid: Boolean get() = right > left && bottom > top
}

/**
 * A single instruction the elderly user follows. Steps are ordered within a
 * guide by [order].
 */
@Serializable
data class Step(
    val id: String,
    val guideId: String,
    val order: Int,
    val instructionText: String,
    /**
     * Where the screenshot lives. Can be:
     *  - null                         → no image
     *  - "sample:bank"/"sample:ewallet" → a bundled placeholder drawable
     *  - "https://…"                  → remote (Firebase Storage) URL
     *  - an absolute file path        → app-private cached file
     */
    val imagePath: String? = null,
    val highlight: HighlightRect? = null,
    val targetType: TargetType = TargetType.NONE,
    val targetValue: String? = null,
)

/**
 * A named, ordered set of steps authored by a caregiver and played back on an
 * elderly device. [languageCode] drives the Text-to-Speech voice.
 */
@Serializable
data class Guide(
    val id: String,
    val authorId: String,
    val title: String,
    val iconEmoji: String = "📘", // 📘
    val languageCode: String = "en",
    val updatedAt: Long = 0L,
    val steps: List<Step> = emptyList(),
)

/**
 * A link between a caregiver account and an elderly device. Created PENDING by
 * the caregiver (with a 6-digit [code]); the elderly device claims it, setting
 * [elderlyId] and moving it to ACTIVE.
 */
@Serializable
data class Pairing(
    val id: String,
    val caregiverId: String,
    val elderlyId: String? = null,
    val code: String,
    val createdAt: Long = 0L,
    val status: PairingStatus = PairingStatus.PENDING,
    val elderlyLabel: String? = null, // friendly name shown to the caregiver
)

/** User preferences. Persisted in DataStore; see SettingsRepository. */
data class UserSettings(
    val role: UserRole = UserRole.UNSET,
    val languageCode: String = "en",
    val fontScale: Float = 1.3f,      // elderly default is intentionally large
    val speechRate: Float = 0.9f,     // slightly slower than normal
    val overlayEnabled: Boolean = false,
    val onboarded: Boolean = false,
)

/** Languages the app ships TTS + UI support for. */
enum class AppLanguage(val code: String, val displayName: String, val nativeName: String) {
    ENGLISH("en", "English", "English"),
    MALAY("ms", "Malay", "Bahasa Melayu"),
    CHINESE("zh", "Chinese", "中文"),
    TAMIL("ta", "Tamil", "தமிழ்");

    companion object {
        fun fromCode(code: String): AppLanguage =
            entries.firstOrNull { it.code == code } ?: ENGLISH
    }
}
