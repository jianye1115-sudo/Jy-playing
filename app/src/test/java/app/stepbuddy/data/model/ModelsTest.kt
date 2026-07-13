package app.stepbuddy.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {

    @Test
    fun highlightRect_validity() {
        assertTrue(HighlightRect(0f, 0f, 1f, 1f).isValid)
        assertFalse(HighlightRect(0.5f, 0.5f, 0.5f, 0.5f).isValid) // zero area
        assertFalse(HighlightRect(1f, 1f, 0f, 0f).isValid)         // inverted
    }

    @Test
    fun appLanguage_fromCode_knownAndFallback() {
        assertEquals(AppLanguage.CHINESE, AppLanguage.fromCode("zh"))
        assertEquals(AppLanguage.TAMIL, AppLanguage.fromCode("ta"))
        assertEquals(AppLanguage.MALAY, AppLanguage.fromCode("ms"))
        // Unknown codes fall back to English.
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromCode("xx"))
    }
}
