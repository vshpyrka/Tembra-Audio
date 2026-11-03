package audio.tembra.ui.platform

/**
 * The microphone access states the tuner reacts to (data-model.md §5); iOS never reports [DeniedCanAsk]. [Unavailable]
 * (feature 011, spec FR-002) is a device with no audio input at all, such as a desktop without a microphone: it is
 * reported before any prompt and is not a refusal.
 */
enum class MicrophonePermissionStatus {
    NotDetermined,
    Granted,
    /** Denied once; the platform prompt can be shown again (Android rationale state). */
    DeniedCanAsk,
    /** Denied for good; only the system settings can change it. */
    DeniedOpenSettings,
    /** The device has no microphone (none built in, none plugged in); nothing can be allowed until one appears. */
    Unavailable;

    val isDenied: Boolean
        get() = this == DeniedCanAsk || this == DeniedOpenSettings
}
