package audio.tembra.ui.platform

/**
 * The device's current audio route as the recording session reads it (feature 010, data-model.md §4): whether the
 * output is the built-in speaker (monitoring must stay silent, spec FR-017), and the names of the output and the
 * input in use (the input's [inputId] changes when a wired headset or an interface is plugged in, FR-021).
 */
data class AudioRoute(
    val outputIsSpeaker: Boolean,
    val outputName: String,
    val inputName: String,
    val inputId: String,
) {
    companion object {
        /** Before the platform reported anything: the phone's own speaker and microphone. */
        val BuiltIn = AudioRoute(outputIsSpeaker = true, outputName = "", inputName = "", inputId = "")
    }
}
