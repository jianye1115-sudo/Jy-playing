package app.stepbuddy.data.repository

import app.stepbuddy.data.remote.FirebaseSync
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The invite link is built from the same host as the App Link intent filter and
 * assetlinks.json. Constructing FirebaseSync here is side-effect free (its
 * Firebase accessors are lazy and never touched by inviteLink).
 */
class PairingRepositoryTest {

    private val repo = PairingRepository(FirebaseSync())

    @Test
    fun inviteLink_usesVerifiedHostAndCode() {
        assertEquals("https://stepbuddy.app/pair?code=123456", repo.inviteLink("123456"))
    }

    @Test
    fun inviteLink_matchesConstants() {
        val link = repo.inviteLink("000000")
        assertTrue(link.startsWith("https://${PairingRepository.INVITE_HOST}/pair?code="))
    }
}
