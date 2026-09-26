package audio.tembra.ui.platform

/** The microphone access states the tuner reacts to (data-model.md §5); iOS never reports [DeniedCanAsk]. */
enum class MicrophonePermissionStatus {
    NotDetermined,
    Granted,
    /** Denied once; the platform prompt can be shown again (Android rationale state). */
    DeniedCanAsk,
    /** Denied for good; only the system settings can change it. */
    DeniedOpenSettings;

    val isDenied: Boolean
        get() = this == DeniedCanAsk || this == DeniedOpenSettings
}
