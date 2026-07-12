package app.stepbuddy.data.local

import app.stepbuddy.data.model.Guide
import app.stepbuddy.data.model.HighlightRect
import app.stepbuddy.data.model.Pairing
import app.stepbuddy.data.model.PairingStatus
import app.stepbuddy.data.model.Step
import app.stepbuddy.data.model.TargetType
import kotlinx.serialization.json.Json

/** Entity <-> domain mapping helpers. Kept in one place so the shapes stay in sync. */
private val json = Json { ignoreUnknownKeys = true }

fun GuideEntity.toDomain(steps: List<Step>): Guide = Guide(
    id = id,
    authorId = authorId,
    title = title,
    iconEmoji = iconEmoji,
    languageCode = languageCode,
    updatedAt = updatedAt,
    steps = steps,
)

fun Guide.toEntity(): GuideEntity = GuideEntity(
    id = id,
    authorId = authorId,
    title = title,
    iconEmoji = iconEmoji,
    languageCode = languageCode,
    updatedAt = updatedAt,
)

fun StepEntity.toDomain(): Step = Step(
    id = id,
    guideId = guideId,
    order = order,
    instructionText = instructionText,
    imagePath = imagePath,
    highlight = highlightJson?.let { runCatching { json.decodeFromString(HighlightRect.serializer(), it) }.getOrNull() },
    targetType = runCatching { TargetType.valueOf(targetType) }.getOrDefault(TargetType.NONE),
    targetValue = targetValue,
)

fun Step.toEntity(): StepEntity = StepEntity(
    id = id,
    guideId = guideId,
    order = order,
    instructionText = instructionText,
    imagePath = imagePath,
    highlightJson = highlight?.let { json.encodeToString(HighlightRect.serializer(), it) },
    targetType = targetType.name,
    targetValue = targetValue,
)

fun PairingEntity.toDomain(): Pairing = Pairing(
    id = id,
    caregiverId = caregiverId,
    elderlyId = elderlyId,
    code = code,
    createdAt = createdAt,
    status = runCatching { PairingStatus.valueOf(status) }.getOrDefault(PairingStatus.PENDING),
    elderlyLabel = elderlyLabel,
)

fun Pairing.toEntity(): PairingEntity = PairingEntity(
    id = id,
    caregiverId = caregiverId,
    elderlyId = elderlyId,
    code = code,
    createdAt = createdAt,
    status = status.name,
    elderlyLabel = elderlyLabel,
)
