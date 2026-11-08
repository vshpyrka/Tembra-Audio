package audio.tembra.ui.shortcuts

/**
 * The mixer's targets for one focused strip (feature 012, spec FR-028; contracts/keyboard-shortcuts.md after 011): the
 * strip that holds keyboard focus answers for its own track. The fader and the knob consume their keys first; what
 * reaches the strip acts on its fader (the steps and pages, home) and its toggles. The master strip serves the fader
 * calls alone.
 */
interface MixerShortcuts {
    /** The fader by one step (0.5 dB; the master 1 %): [direction] 1 up, −1 down. */
    fun faderStep(direction: Int): Boolean

    /** The pan by one percent: [direction] 1 right, −1 left; false on the master. */
    fun knobStep(direction: Int): Boolean

    /** A page of the fader (6 dB; the master 10 %). */
    fun page(direction: Int): Boolean

    /** The fader home: 0 dB, the master's 80 %. */
    fun home(): Boolean

    fun mute(): Boolean

    fun solo(): Boolean
}
