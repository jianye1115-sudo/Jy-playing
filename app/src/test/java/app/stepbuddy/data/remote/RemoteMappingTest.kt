package app.stepbuddy.data.remote

import app.stepbuddy.data.model.HighlightRect
import app.stepbuddy.data.model.Step
import app.stepbuddy.data.model.TargetType
import org.junit.Assert.assertEquals
import org.junit.Test

/** Step <-> Firestore StepDoc mapping (incl. the normalized highlight map). */
class RemoteMappingTest {

    @Test
    fun step_roundTrips_throughDoc() {
        val step = Step(
            id = "s1",
            guideId = "g1",
            order = 0,
            instructionText = "Tap Transfer",
            imagePath = "https://img.example/1.jpg",
            highlight = HighlightRect(0.25f, 0.5f, 0.75f, 1.0f),
            targetType = TargetType.APP,
            targetValue = "com.bank.app",
        )
        val back = step.toDoc(imageUrl = step.imagePath).toDomain()

        assertEquals(step.id, back.id)
        assertEquals(step.instructionText, back.instructionText)
        assertEquals(step.imagePath, back.imagePath)
        assertEquals(step.highlight, back.highlight)
        assertEquals(step.targetType, back.targetType)
        assertEquals(step.targetValue, back.targetValue)
    }

    @Test
    fun highlight_isOmitted_whenNull() {
        val step = Step(id = "s", guideId = "g", order = 1, instructionText = "x")
        val doc = step.toDoc(imageUrl = null)
        assertEquals(null, doc.highlight)
        assertEquals(null, doc.toDomain().highlight)
    }
}
