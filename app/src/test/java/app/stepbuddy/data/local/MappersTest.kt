package app.stepbuddy.data.local

import app.stepbuddy.data.model.Guide
import app.stepbuddy.data.model.HighlightRect
import app.stepbuddy.data.model.Step
import app.stepbuddy.data.model.TargetType
import org.junit.Assert.assertEquals
import org.junit.Test

/** Room entity <-> domain mapping is lossless. Uses binary-exact floats. */
class MappersTest {

    private val step = Step(
        id = "s1",
        guideId = "g1",
        order = 2,
        instructionText = "Tap here",
        imagePath = "/data/x.jpg",
        highlight = HighlightRect(0.25f, 0.5f, 0.75f, 1.0f),
        targetType = TargetType.URL,
        targetValue = "https://example.com",
    )

    @Test
    fun step_roundTrips_throughEntity() {
        assertEquals(step, step.toEntity().toDomain())
    }

    @Test
    fun step_withoutHighlight_roundTrips() {
        val plain = step.copy(highlight = null, imagePath = null, targetType = TargetType.NONE, targetValue = null)
        assertEquals(plain, plain.toEntity().toDomain())
    }

    @Test
    fun guide_roundTrips_throughEntity() {
        val guide = Guide(
            id = "g1", authorId = "a1", title = "Send money", iconEmoji = "🏦",
            languageCode = "ms", updatedAt = 5L, steps = listOf(step),
        )
        assertEquals(guide, guide.toEntity().toDomain(guide.steps))
    }
}
