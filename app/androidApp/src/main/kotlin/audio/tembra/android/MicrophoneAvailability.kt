package audio.tembra.android

/**
 * Whether the device has a microphone to listen through (feature 011, spec FR-002, research R2): desktops and
 * Chromebooks may have none, and USB microphones come and go. Read on every refresh of [AndroidMicrophonePermission];
 * the device tests fake it.
 */
fun interface MicrophoneAvailability {
    fun hasInput(): Boolean
}
