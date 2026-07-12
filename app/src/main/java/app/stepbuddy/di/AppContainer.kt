package app.stepbuddy.di

import android.content.Context
import app.stepbuddy.data.export.GuideExporter
import app.stepbuddy.data.local.StepBuddyDatabase
import app.stepbuddy.data.remote.FirebaseSync
import app.stepbuddy.data.repository.GuideRepository
import app.stepbuddy.data.repository.PairingRepository
import app.stepbuddy.data.repository.SyncManager
import app.stepbuddy.data.settings.SettingsRepository
import app.stepbuddy.service.PlaybackController
import app.stepbuddy.tts.TtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Tiny manual dependency container held by [app.stepbuddy.StepBuddyApp].
 *
 * We deliberately avoid a DI framework (Hilt/Dagger) here: the graph is small,
 * and manual wiring keeps the project readable and free of annotation-processor
 * setup. Everything below is a process-wide singleton.
 */
class AppContainer(context: Context) {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settingsRepository = SettingsRepository(context)

    private val database = StepBuddyDatabase.get(context)
    private val dao = database.dao()

    val firebaseSync = FirebaseSync()

    val guideRepository = GuideRepository(dao, firebaseSync)
    val pairingRepository = PairingRepository(firebaseSync)

    val ttsManager = TtsManager(context)
    val playbackController = PlaybackController(ttsManager)

    val guideExporter = GuideExporter(context)

    val syncManager = SyncManager(settingsRepository, firebaseSync, guideRepository)
}
