package app.stepbuddy

import android.app.Application
import app.stepbuddy.di.AppContainer
import app.stepbuddy.service.GuidePlaybackService

/**
 * Application entry point. Builds the [AppContainer] (manual DI) and kicks off
 * the elderly-side sync, which quietly no-ops for the caregiver role and when
 * Firebase is unreachable.
 */
class StepBuddyApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Notification channel must exist before the playback service posts.
        GuidePlaybackService.createChannel(this)

        // Start listening for pushed guides (ELDERLY role). Safe to call always.
        container.syncManager.start(container.applicationScope)
    }
}
