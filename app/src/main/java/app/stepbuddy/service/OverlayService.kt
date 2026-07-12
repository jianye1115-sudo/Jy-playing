package app.stepbuddy.service

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import app.stepbuddy.R
import app.stepbuddy.StepBuddyApp
import kotlinx.coroutines.launch

/**
 * OPTIONAL floating overlay bubble (SYSTEM_ALERT_WINDOW) with the same controls
 * as the notification, floating on top of other apps.
 *
 * This is BEST-EFFORT only. Many secure apps use FLAG_SECURE and the system
 * hides overlays over them; some OEMs restrict overlays entirely. We therefore:
 *   - never start unless [canDraw] is true (permission granted), and
 *   - treat the persistent notification (GuidePlaybackService) as the primary,
 *     always-reliable channel.
 * If the bubble can't be shown we simply don't — no crash, no error dialog.
 *
 * Built with classic Android views (not Compose) so it can live in a raw
 * WindowManager window without a Compose lifecycle/host.
 */
class OverlayService : LifecycleService() {

    private val controller get() = (application as StepBuddyApp).container.playbackController
    private var windowManager: WindowManager? = null
    private var root: LinearLayout? = null
    private var stepLabel: TextView? = null

    override fun onCreate() {
        super.onCreate()
        if (!canDraw(this)) { // permission revoked meanwhile — fall back silently
            stopSelf()
            return
        }
        addBubble()
        lifecycleScope.launch {
            controller.state.collect { state ->
                if (state == null) stopSelf()
                else stepLabel?.text = getString(R.string.playback_step_of, state.humanStep, state.total)
            }
        }
    }

    private fun addBubble() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F2004D40"))
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        val label = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setPadding(dp(8), dp(4), dp(8), dp(8))
            text = controller.state.value?.let {
                getString(R.string.playback_step_of, it.humanStep, it.total)
            } ?: getString(R.string.notif_guidance_ongoing)
        }
        stepLabel = label
        container.addView(label)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(bigButton(getString(R.string.playback_repeat)) { controller.repeat() })
        row.addView(bigButton(getString(R.string.playback_back)) { controller.back() })
        row.addView(bigButton(getString(R.string.playback_next)) { controller.next() })
        row.addView(bigButton(getString(R.string.playback_close)) { controller.stop() })
        container.addView(row)
        root = container

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12); y = dp(120)
        }
        makeDraggable(container, params, wm)

        // Adding the view can throw on some OEMs even with permission — guard it.
        runCatching { wm.addView(container, params) }.onFailure { stopSelf() }
    }

    private fun bigButton(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        minHeight = dp(56)          // >= 56dp touch target
        minWidth = dp(72)
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.parseColor("#00695C"))
        val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        lp.setMargins(dp(4), dp(4), dp(4), dp(4))
        layoutParams = lp
        setOnClickListener { onClick() }
    }

    private fun makeDraggable(view: View, params: WindowManager.LayoutParams, wm: WindowManager) {
        var startX = 0; var startY = 0; var touchX = 0f; var touchY = 0f
        view.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x; startY = params.y
                    touchX = e.rawX; touchY = e.rawY; true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (e.rawX - touchX).toInt()
                    params.y = startY + (e.rawY - touchY).toInt()
                    runCatching { wm.updateViewLayout(view, params) }; true
                }
                else -> false
            }
        }
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        root?.let { runCatching { windowManager?.removeView(it) } }
        root = null
        super.onDestroy()
    }

    companion object {
        /** True only when the user has granted "draw over other apps". */
        fun canDraw(context: Context): Boolean = Settings.canDrawOverlays(context)

        fun start(context: Context) {
            if (!canDraw(context)) return
            context.startService(Intent(context, OverlayService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }
}
