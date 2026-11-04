package audio.tembra.ui.tuner

/**
 * What Auto Listen shows in place of its meter when it cannot listen (spec FR-015 of feature 003, FR-002 of feature
 * 011): the permission was refused, or the device has no microphone at all.
 */
internal enum class MicrophoneNotice {
    /** Access was refused; one button asks again or opens the system settings. */
    Denied,

    /** There is no microphone; nothing to ask for. */
    Unavailable,
}
