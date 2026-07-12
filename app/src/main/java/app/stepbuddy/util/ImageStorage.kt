package app.stepbuddy.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

/**
 * Copies caregiver-picked screenshots into app-private storage so they survive
 * and can be uploaded to Firebase Storage on the next push. Screenshots never
 * leave the caregiver's control except as part of a guide they choose to share.
 */
object ImageStorage {
    fun copyToPrivate(context: Context, uri: Uri): String? = runCatching {
        val dir = File(context.filesDir, "screenshots").apply { mkdirs() }
        val dest = File(dir, "shot_${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(uri)!!.use { input ->
            dest.outputStream().use { input.copyTo(it) }
        }
        dest.absolutePath
    }.getOrNull()
}
