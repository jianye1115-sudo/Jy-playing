package app.stepbuddy.data.remote

import app.stepbuddy.data.model.Guide
import app.stepbuddy.data.model.HighlightRect
import app.stepbuddy.data.model.Pairing
import app.stepbuddy.data.model.PairingStatus
import app.stepbuddy.data.model.Step
import app.stepbuddy.data.model.TargetType

/**
 * Firestore document shapes. They MUST have a no-arg constructor (all fields
 * defaulted) so Firestore's `toObject()` can hydrate them by reflection.
 *
 * Firestore layout:
 *   guides/{guideId}                     -> GuideDoc
 *   guides/{guideId}/steps/{stepId}      -> StepDoc
 *   pairings/{code}                      -> PairingDoc   (doc id == 6-digit code)
 */
// NOTE: fields are `var` with defaults so Firestore's reflective `toObject()`
// can hydrate them (it needs the generated no-arg constructor + settable fields).
data class GuideDoc(
    var id: String = "",
    var authorId: String = "",
    var title: String = "",
    var iconEmoji: String = "📘",
    var languageCode: String = "en",
    var updatedAt: Long = 0L,
)

data class StepDoc(
    var id: String = "",
    var guideId: String = "",
    var order: Int = 0,
    var instructionText: String = "",
    // Screenshots live in Firebase Storage; only the download URL syncs.
    var imageUrl: String? = null,
    var highlight: Map<String, Double>? = null, // keys: left, top, right, bottom
    var targetType: String = "NONE",
    var targetValue: String? = null,
)

data class PairingDoc(
    var id: String = "",
    var caregiverId: String = "",
    var elderlyId: String? = null,
    var code: String = "",
    var createdAt: Long = 0L,
    var status: String = "PENDING",
    var elderlyLabel: String? = null,
)

// ---- DTO <-> domain mapping ----

fun GuideDoc.toDomain(steps: List<Step>) = Guide(
    id = id, authorId = authorId, title = title, iconEmoji = iconEmoji,
    languageCode = languageCode, updatedAt = updatedAt, steps = steps,
)

fun Guide.toDoc() = GuideDoc(
    id = id, authorId = authorId, title = title, iconEmoji = iconEmoji,
    languageCode = languageCode, updatedAt = updatedAt,
)

fun StepDoc.toDomain() = Step(
    id = id,
    guideId = guideId,
    order = order,
    instructionText = instructionText,
    imagePath = imageUrl, // remote URL; Coil loads it and Room caches the string
    highlight = highlight?.let {
        HighlightRect(
            left = (it["left"] ?: 0.0).toFloat(),
            top = (it["top"] ?: 0.0).toFloat(),
            right = (it["right"] ?: 0.0).toFloat(),
            bottom = (it["bottom"] ?: 0.0).toFloat(),
        )
    },
    targetType = runCatching { TargetType.valueOf(targetType) }.getOrDefault(TargetType.NONE),
    targetValue = targetValue,
)

fun Step.toDoc(imageUrl: String?) = StepDoc(
    id = id,
    guideId = guideId,
    order = order,
    instructionText = instructionText,
    imageUrl = imageUrl,
    highlight = highlight?.let {
        mapOf(
            "left" to it.left.toDouble(),
            "top" to it.top.toDouble(),
            "right" to it.right.toDouble(),
            "bottom" to it.bottom.toDouble(),
        )
    },
    targetType = targetType.name,
    targetValue = targetValue,
)

fun PairingDoc.toDomain() = Pairing(
    id = id.ifEmpty { code },
    caregiverId = caregiverId,
    elderlyId = elderlyId,
    code = code,
    createdAt = createdAt,
    status = runCatching { PairingStatus.valueOf(status) }.getOrDefault(PairingStatus.PENDING),
    elderlyLabel = elderlyLabel,
)
