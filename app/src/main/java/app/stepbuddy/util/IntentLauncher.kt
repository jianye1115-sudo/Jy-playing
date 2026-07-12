package app.stepbuddy.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import app.stepbuddy.data.model.Step
import app.stepbuddy.data.model.TargetType

/**
 * Launches a step's target when the elderly user taps "Ready — open …".
 *
 * StepBuddy never performs the task itself; it only OPENS the app or page so the
 * user does the tapping. Failures (app not installed, bad URL) degrade to a
 * short message and, for apps, a Play Store fallback — never a crash.
 */
object IntentLauncher {

    fun launchTarget(context: Context, step: Step) {
        when (step.targetType) {
            TargetType.URL -> step.targetValue?.let { openUrl(context, it) }
            TargetType.APP -> step.targetValue?.let { openApp(context, it) }
            TargetType.NONE -> Unit
        }
    }

    fun openUrl(context: Context, url: String) {
        val normalized = if (url.startsWith("http")) url else "https://$url"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalized))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            toast(context, "Couldn't open the page.")
        }
    }

    fun openApp(context: Context, packageName: String) {
        val launch = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launch)
        } else {
            // App not installed — send them to its Play Store page instead.
            openPlayStore(context, packageName)
        }
    }

    private fun openPlayStore(context: Context, packageName: String) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(market)
        } catch (e: ActivityNotFoundException) {
            openUrl(context, "https://play.google.com/store/apps/details?id=$packageName")
        }
    }

    private fun toast(context: Context, message: String) =
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
