package app.stepbuddy

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runtime smoke test — runs on a real emulator via `connectedDebugAndroidTest`.
 * It confirms the app actually LAUNCHES (a fresh install lands on onboarding)
 * and that the role picker navigates to the language step. This exercises the
 * real Activity, Compose UI, Navigation, DataStore, and the ViewModel wiring —
 * the parts unit tests can't cover.
 *
 * Neither test completes onboarding (never sets the `onboarded` flag), so a
 * fresh install always starts here regardless of test method order.
 */
@RunWith(AndroidJUnit4::class)
class OnboardingSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun app_launches_toOnboardingWelcome() {
        // DataStore loads asynchronously, so wait for the first real frame.
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText("Welcome to StepBuddy").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Welcome to StepBuddy").assertIsDisplayed()
        composeRule.onNodeWithText("Who is using this phone?").assertIsDisplayed()
    }

    @Test
    fun pickingElderlyRole_advancesToLanguageStep() {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText("This is my phone").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("This is my phone").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Choose your language").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Choose your language").assertIsDisplayed()
    }
}
