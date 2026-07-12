package app.stepbuddy.data

import app.stepbuddy.data.model.Guide
import app.stepbuddy.data.model.HighlightRect
import app.stepbuddy.data.model.Step
import app.stepbuddy.data.model.TargetType
import app.stepbuddy.data.repository.GuideRepository

/**
 * Two pre-loaded sample guides so a freshly-installed elderly device has
 * something to try immediately, before any pairing. They use bundled
 * placeholder screenshots (`sample:` image refs) and the reserved
 * [GuideRepository.SAMPLE_AUTHOR_ID] so remote sync never deletes them.
 *
 * NOTE: the screenshots are generic mockups, NOT real bank/wallet UIs, and
 * contain no account numbers — matching the app's privacy rules.
 */
object SampleGuides {

    fun all(): List<Guide> = listOf(onlineBanking(), ewalletTopUp())

    private fun onlineBanking(): Guide {
        val id = "sample-online-banking"
        return Guide(
            id = id,
            authorId = GuideRepository.SAMPLE_AUTHOR_ID,
            title = "Send money with online banking",
            iconEmoji = "🏦",
            languageCode = "en",
            updatedAt = 1_000L,
            steps = listOf(
                Step(
                    id = "$id-1", guideId = id, order = 0,
                    instructionText = "Open your bank app. Type your usual password to log in.",
                    imagePath = "sample:bank",
                    targetType = TargetType.URL,
                    targetValue = "https://www.example.com",
                ),
                Step(
                    id = "$id-2", guideId = id, order = 1,
                    instructionText = "Tap “Transfer” or “Send money”.",
                    imagePath = "sample:bank",
                    highlight = HighlightRect(0.08f, 0.83f, 0.92f, 0.95f),
                ),
                Step(
                    id = "$id-3", guideId = id, order = 2,
                    instructionText = "Choose the person you want to send money to from your saved list.",
                    imagePath = "sample:bank",
                    highlight = HighlightRect(0.08f, 0.45f, 0.92f, 0.56f),
                ),
                Step(
                    id = "$id-4", guideId = id, order = 3,
                    instructionText = "Type the amount. Check it twice before you continue.",
                ),
                Step(
                    id = "$id-5", guideId = id, order = 4,
                    instructionText = "Tap “Confirm”. Enter the code your bank sends by SMS if it asks. Done!",
                ),
            ),
        )
    }

    private fun ewalletTopUp(): Guide {
        val id = "sample-ewallet-topup"
        return Guide(
            id = id,
            authorId = GuideRepository.SAMPLE_AUTHOR_ID,
            title = "Top up e-wallet",
            iconEmoji = "👛",
            languageCode = "en",
            updatedAt = 900L,
            steps = listOf(
                Step(
                    id = "$id-1", guideId = id, order = 0,
                    instructionText = "Open your e-wallet app.",
                    imagePath = "sample:ewallet",
                    targetType = TargetType.URL,
                    targetValue = "https://www.example.com",
                ),
                Step(
                    id = "$id-2", guideId = id, order = 1,
                    instructionText = "Tap “Top up” or “Reload”.",
                    imagePath = "sample:ewallet",
                    highlight = HighlightRect(0.08f, 0.83f, 0.92f, 0.95f),
                ),
                Step(
                    id = "$id-3", guideId = id, order = 2,
                    instructionText = "Choose an amount, for example 50.",
                    imagePath = "sample:ewallet",
                    highlight = HighlightRect(0.67f, 0.44f, 0.92f, 0.53f),
                ),
                Step(
                    id = "$id-4", guideId = id, order = 3,
                    instructionText = "Tap “Pay”. Follow the bank screen if it appears. Your wallet is topped up!",
                ),
            ),
        )
    }
}
