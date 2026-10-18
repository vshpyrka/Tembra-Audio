package audio.tembra.ui.transport

/** The transport bar's intents as plain functions (Constitution II); [None] for previews and tests. */
data class TransportCallbacks(
    val onPlayPause: () -> Unit,
    val onSkipToStart: () -> Unit,
    val onStepBar: (delta: Int) -> Unit,
    val onMasterVolume: (percent: Int) -> Unit,
    val onMetronomeToggle: () -> Unit,
    val onMetronomeSettings: () -> Unit,
) {
    companion object {
        val None = TransportCallbacks(
            onPlayPause = {},
            onSkipToStart = {},
            onStepBar = {},
            onMasterVolume = {},
            onMetronomeToggle = {},
            onMetronomeSettings = {},
        )
    }
}
