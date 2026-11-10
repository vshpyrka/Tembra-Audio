package audio.tembra.ui.shortcuts

import androidx.compose.ui.input.key.Key

/** The shortcut sheet's and the system lists' words (feature 011, spec FR-022). */
internal object ShortcutCopy {
    const val sheetTitle = "Keyboard shortcuts"
    const val close = "Close"
    const val profileRow = "Keyboard shortcuts"
    const val profileRowDetail = "Every key the app answers to"

    fun scopeTitle(scope: ShortcutScope): String = when (scope) {
        ShortcutScope.Everywhere -> "Everywhere"
        ShortcutScope.Timeline -> "Timeline"
        ShortcutScope.Mixer -> "Mixer"
        ShortcutScope.Tuner -> "Tuner"
        ShortcutScope.Metronome -> "Metronome"
        ShortcutScope.Projects -> "Projects"
    }

    const val openTimeline = "Open Timeline"
    const val openTuner = "Open Tuner"
    const val openMetronome = "Open Metronome"
    const val openProjects = "Open Projects"
    const val openProfile = "Open Profile"
    const val showShortcuts = "Show keyboard shortcuts"
    const val closeOverlay = "Close the menu, sheet or dialog"
    const val playPause = "Play or pause"
    const val skipToStart = "Go to the start"
    const val stepBack = "Move the playhead back one grid step"
    const val stepForward = "Move the playhead forward one grid step"
    const val barBack = "Move the playhead back one bar"
    const val barForward = "Move the playhead forward one bar"
    const val split = "Split the selected clip at the playhead"
    const val deleteClip = "Delete the selected clip"
    const val undo = "Undo"
    const val redo = "Redo"
    const val zoomIn = "Zoom in"
    const val zoomOut = "Zoom out"
    const val mute = "Mute or unmute the selected track"
    const val solo = "Solo the selected track"
    const val arm = "Arm the selected track for recording"
    const val record = "Record, or stop recording"
    const val monitor = "Hear the input while armed"
    const val mixer = "Open or close the mixer"
    // Feature 012 (FR-028): the mixer's keys act on the strip that holds keyboard focus.
    const val mixerFaderUp = "Fader up 0.5 dB on the focused strip"
    const val mixerFaderDown = "Fader down 0.5 dB on the focused strip"
    const val mixerKnobRight = "Pan 1 % to the right on the focused strip"
    const val mixerKnobLeft = "Pan 1 % to the left on the focused strip"
    const val mixerPageUp = "Fader up 6 dB, or pan 10 % right on a focused knob"
    const val mixerPageDown = "Fader down 6 dB, or pan 10 % left on a focused knob"
    const val mixerHome = "Fader to 0 dB, or a focused knob to the centre"
    const val mixerMute = "Mute or unmute the focused strip"
    const val mixerSolo = "Solo the focused strip"
    const val startStop = "Start or stop the metronome"
    const val tempoUp = "Tempo up 1 BPM"
    const val tempoDown = "Tempo down 1 BPM"
    const val tempoUpTen = "Tempo up 10 BPM"
    const val tempoDownTen = "Tempo down 10 BPM"
    const val tapTempo = "Tap the tempo"
    const val autoListen = "Auto Listen"
    const val byEar = "By Ear"
    fun playString(number: Int): String = "Play string $number (By Ear)"
    const val stopTone = "Stop the tone"
    const val toggleLoop = "Loop the tone on or off"
    const val focusSearch = "Find a project"
    const val newProject = "New project"

    /** How a chord reads on a key cap, by the platform's modifier. */
    fun keyCaps(chord: KeyChord, primary: PrimaryModifier): List<String> = buildList {
        if (chord.primary) add(if (primary == PrimaryModifier.Ctrl) "Ctrl" else "⌘")
        if (chord.shift) add("Shift")
        add(
            when (val key = chord.key) {
                is ChordKey.Character -> if (key.char == '-') "−" else key.char.uppercaseChar().toString()
                is ChordKey.Named -> namedKey(key.key)
            },
        )
    }

    private fun namedKey(key: Key): String = when (key) {
        Key.Spacebar -> "Space"
        Key.Escape -> "Esc"
        Key.MoveHome -> "Home"
        Key.Delete -> "Delete"
        Key.Backspace -> "Backspace"
        Key.DirectionLeft -> "←"
        Key.DirectionRight -> "→"
        Key.DirectionUp -> "↑"
        Key.DirectionDown -> "↓"
        else -> key.toString()
    }
}
