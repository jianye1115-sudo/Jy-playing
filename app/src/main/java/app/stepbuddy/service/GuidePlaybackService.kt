package app.stepbuddy.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import app.stepbuddy.MainActivity
import app.stepbuddy.R
import app.stepbuddy.StepBuddyApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps guidance alive while the elderly user switches
 * to their bank/browser app. It owns the PERSISTENT, expandable notification
 * ("Step 2 of 6") with big actions: Repeat, Back, Next, Close.
 *
 * It is a thin shell around [PlaybackController]: it observes the controller's
 * [PlaybackController.state] and re-renders the notification on every change,
 * and its action buttons simply call back into the controller. That keeps the
 * notification and the on-screen UI in lock-step. See [PlaybackController].
 *
 * This is the PRIMARY, always-reliable guidance channel — the optional overlay
 * bubble is best-effort on top.
 */
class GuidePlaybackService : LifecycleService() {

    private val container get() = (application as StepBuddyApp).container
    private val controller get() = container.playbackController

    override fun onCreate() {
        super.onCreate()
        createChannel(this)
        // Re-render the notification on each state change. We only tear the
        // service down AFTER playback has actually been active, so the initial
        // null (emitted before onStartCommand loads the guide) doesn't stop us.
        lifecycleScope.launch {
            controller.state.collect { state ->
                if (state != null) {
                    everActive = true
                    notificationManager().notify(NOTIF_ID, buildNotification(state))
                } else if (everActive) {
                    stopEverything()
                }
            }
        }
    }

    private var everActive = false

    private fun stopEverything() {
        OverlayService.stop(this)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> handleStart(intent.getStringExtra(EXTRA_GUIDE_ID))
            ACTION_REPEAT -> controller.repeat()
            ACTION_NEXT -> controller.next()
            ACTION_BACK -> controller.back()
            ACTION_STOP -> controller.stop()
        }
        return START_STICKY
    }

    private fun handleStart(guideId: String?) {
        // Promote to foreground immediately with a placeholder so the 5s window
        // is satisfied even before the guide finishes loading from Room.
        startForegroundCompat(placeholderNotification())
        if (guideId == null) {
            stopEverything(); return
        }
        lifecycleScope.launch {
            val guide = container.guideRepository.getGuide(guideId)
            val settings = container.settingsRepository.settings.first()
            if (guide == null) {
                // Nothing to play and we never went active — stop explicitly.
                stopEverything()
                return@launch
            }
            controller.start(guide, settings.speechRate)
            notificationManager().notify(NOTIF_ID, buildNotification(controller.state.value ?: return@launch))
            // Best-effort overlay bubble on top of other apps, where allowed.
            if (settings.overlayEnabled && OverlayService.canDraw(this@GuidePlaybackService)) {
                OverlayService.start(this@GuidePlaybackService)
            }
        }
    }

    // ------------------------------------------------------------ Notification

    private fun buildNotification(state: PlaybackController.State): Notification {
        val title = getString(R.string.playback_step_of, state.humanStep, state.total)
        val text = state.step.instructionText

        return baseBuilder()
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openAppIntent())
            .addAction(android.R.drawable.ic_menu_rotate, getString(R.string.playback_repeat), action(ACTION_REPEAT))
            .addAction(android.R.drawable.ic_media_previous, getString(R.string.playback_back), action(ACTION_BACK))
            .addAction(android.R.drawable.ic_media_next, getString(R.string.playback_next), action(ACTION_NEXT))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.playback_close), action(ACTION_STOP))
            .build()
    }

    private fun placeholderNotification(): Notification =
        baseBuilder()
            .setContentTitle(getString(R.string.notif_guidance_ongoing))
            .build()

    private fun baseBuilder(): NotificationCompat.Builder =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)                       // persistent — can't be swiped away
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)

    private fun action(action: String): PendingIntent {
        val intent = Intent(this, GuidePlaybackService::class.java).setAction(action)
        return PendingIntent.getService(
            this, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_PLAYBACK)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun startForegroundCompat(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else 0
        ServiceCompat.startForeground(this, NOTIF_ID, notification, type)
    }

    private fun notificationManager() =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_ID = "guide_playback"
        const val NOTIF_ID = 42

        const val ACTION_START = "app.stepbuddy.action.START"
        const val ACTION_REPEAT = "app.stepbuddy.action.REPEAT"
        const val ACTION_NEXT = "app.stepbuddy.action.NEXT"
        const val ACTION_BACK = "app.stepbuddy.action.BACK"
        const val ACTION_STOP = "app.stepbuddy.action.STOP"
        const val EXTRA_GUIDE_ID = "guideId"

        /** Start playing [guideId] in the foreground service. */
        fun start(context: Context, guideId: String) {
            val intent = Intent(context, GuidePlaybackService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_GUIDE_ID, guideId)
            context.startForegroundService(intent)
        }

        fun createChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(R.string.notif_channel_desc) }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }
}
