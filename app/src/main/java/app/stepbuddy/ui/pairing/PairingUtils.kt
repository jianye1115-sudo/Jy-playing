package app.stepbuddy.ui.pairing

import android.net.Uri

/**
 * Extracts the 6-digit pairing code from anything a QR/scan or invite link can
 * carry: a full App Link, the custom stepbuddy:// scheme, or a bare code.
 */
object PairingUtils {
    fun parseCode(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        // Try to read a ?code= query param first.
        runCatching {
            val fromQuery = Uri.parse(raw).getQueryParameter("code")?.filter { it.isDigit() }
            if (fromQuery?.length == 6) return fromQuery
        }
        // Otherwise fall back to the first run of 6 digits in the string.
        val digits = raw.filter { it.isDigit() }
        return if (digits.length >= 6) digits.take(6) else null
    }
}
