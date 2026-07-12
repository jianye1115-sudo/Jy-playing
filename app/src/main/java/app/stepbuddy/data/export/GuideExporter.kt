package app.stepbuddy.data.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import app.stepbuddy.data.model.Guide
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Cloud-free fallback: pack a [Guide] into a single ".stepbuddy" file and read
 * it back. The file is just a ZIP:
 *
 *   guide.json          — the guide + steps (image paths rewritten to relative)
 *   images/<stepId>.img — any local screenshot bytes
 *
 * Auto-sync via pairing is the default path; this exists so a caregiver with no
 * account can still share a guide over WhatsApp/email, and the elderly device
 * can import it offline.
 */
class GuideExporter(private val context: Context) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private val exportsDir: File get() = File(context.filesDir, "exports").apply { mkdirs() }
    private val screenshotsDir: File get() = File(context.filesDir, "screenshots").apply { mkdirs() }

    /** Write [guide] to files/exports/<title>.stepbuddy and return a shareable Uri. */
    fun export(guide: Guide): Uri {
        val safeName = guide.title.ifBlank { "guide" }.replace(Regex("[^A-Za-z0-9-_ ]"), "_")
        val outFile = File(exportsDir, "$safeName.stepbuddy")

        // Rewrite local image paths to archive-relative entries; keep remote
        // URLs and bundled `sample:` refs unchanged so they still resolve.
        val rewrittenSteps = guide.steps.map { step ->
            val path = step.imagePath
            if (path != null && !path.startsWith("http") && !path.startsWith("sample:") && File(path).exists()) {
                step.copy(imagePath = "images/${step.id}")
            } else step
        }
        val portable = guide.copy(steps = rewrittenSteps)

        ZipOutputStream(outFile.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("guide.json"))
            zip.write(json.encodeToString(Guide.serializer(), portable).toByteArray())
            zip.closeEntry()

            guide.steps.forEach { step ->
                val path = step.imagePath ?: return@forEach
                if (path.startsWith("http") || path.startsWith("sample:")) return@forEach
                val file = File(path)
                if (!file.exists()) return@forEach
                zip.putNextEntry(ZipEntry("images/${step.id}"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
    }

    /** Read a ".stepbuddy" file, extracting images locally and returning the guide. */
    fun import(uri: Uri): Result<Guide> = runCatching {
        var guideJson: String? = null
        val extracted = mutableMapOf<String, String>() // stepId -> local absolute path

        context.contentResolver.openInputStream(uri)!!.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    when {
                        entry.name == "guide.json" -> guideJson = zip.readBytes().decodeToString()
                        entry.name.startsWith("images/") -> {
                            val stepId = entry.name.removePrefix("images/")
                            val dest = File(screenshotsDir, "imported_${UUID.randomUUID()}")
                            dest.outputStream().use { zip.copyTo(it) }
                            extracted[stepId] = dest.absolutePath
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }

        val parsed = json.decodeFromString(Guide.serializer(), requireNotNull(guideJson) { "Missing guide.json" })
        // Point each step at its freshly-extracted local image.
        val steps = parsed.steps.map { step ->
            val local = extracted[step.id]
            if (local != null) step.copy(imagePath = local) else step
        }
        parsed.copy(updatedAt = System.currentTimeMillis(), steps = steps)
    }
}
