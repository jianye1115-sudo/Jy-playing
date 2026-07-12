package app.stepbuddy.data.local

import androidx.room.TypeConverter
import app.stepbuddy.data.model.HighlightRect
import kotlinx.serialization.json.Json

/** Room TypeConverters. HighlightRect is stored as a compact JSON string. */
class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun highlightToJson(rect: HighlightRect?): String? =
        rect?.let { json.encodeToString(HighlightRect.serializer(), it) }

    @TypeConverter
    fun jsonToHighlight(value: String?): HighlightRect? =
        value?.let { runCatching { json.decodeFromString(HighlightRect.serializer(), it) }.getOrNull() }
}
