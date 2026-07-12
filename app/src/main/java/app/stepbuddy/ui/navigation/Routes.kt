package app.stepbuddy.ui.navigation

/** All navigation destinations. Arguments are appended as query params. */
object Routes {
    const val ONBOARDING = "onboarding"

    // Caregiver
    const val CAREGIVER_HOME = "caregiver_home"
    const val GUIDE_EDITOR = "guide_editor"          // ?guideId= (omit to create)
    const val CAREGIVER_PAIRING = "caregiver_pairing"
    const val CAREGIVER_DEVICES = "caregiver_devices"

    // Elderly
    const val ELDERLY_HOME = "elderly_home"
    const val PLAYBACK = "playback"
    const val ELDERLY_PAIRING = "elderly_pairing"    // ?code= (pre-fill from invite link)

    // Shared
    const val SETTINGS = "settings"

    fun guideEditor(guideId: String? = null) =
        if (guideId == null) GUIDE_EDITOR else "$GUIDE_EDITOR?guideId=$guideId"

    fun elderlyPairing(code: String? = null) =
        if (code == null) ELDERLY_PAIRING else "$ELDERLY_PAIRING?code=$code"
}
