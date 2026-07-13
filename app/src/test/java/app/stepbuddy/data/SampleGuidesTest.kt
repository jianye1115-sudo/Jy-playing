package app.stepbuddy.data

import app.stepbuddy.data.repository.GuideRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The two pre-loaded guides are well-formed and protected from sync deletion. */
class SampleGuidesTest {

    @Test
    fun exposesTwoGuides() {
        assertEquals(2, SampleGuides.all().size)
    }

    @Test
    fun everyGuideIsValid() {
        SampleGuides.all().forEach { guide ->
            // Protected author id so remote sync never deletes the samples.
            assertEquals(GuideRepository.SAMPLE_AUTHOR_ID, guide.authorId)
            assertTrue("guide has steps", guide.steps.isNotEmpty())
            // Step orders are sequential 0..n-1.
            guide.steps.forEachIndexed { index, step -> assertEquals(index, step.order) }
            // Step ids are unique within the guide.
            assertEquals(guide.steps.size, guide.steps.map { it.id }.toSet().size)
            // No blank instructions.
            assertTrue(guide.steps.all { it.instructionText.isNotBlank() })
        }
    }
}
