package audio.tembra.ui.metronome

import audio.tembra.core.metronome.BeatLevel
import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TempoMarking

/** Every user-visible string of the metronome (contracts/metronome-screen.md, contracts/click-sounds.md), asserted by the tests. */
internal object MetronomeCopy {
    // Section labels: drawn uppercase, announced in sentence case.
    const val timeSignature = "Time signature"
    const val commonAndComplex = "Common & Complex"
    const val rhythmSubdivision = "Rhythm subdivision"
    const val style = "Style"
    const val clickTimbre = "Click timbre"
    const val barShape = "Bar shape"

    // A menu trigger's state (the signature pill, the sound pill).
    const val expanded = "Expanded"
    const val collapsed = "Collapsed"

    // Tempo.
    const val bpm = "BPM"
    const val increaseTempo = "Increase tempo"
    const val decreaseTempo = "Decrease tempo"

    // Tap tempo and play.
    const val tapTempo = "Tap tempo"
    const val tapRest = "Sync finger rhythm"
    const val tapAgain = "Tap again"
    const val startMetronome = "Start metronome"
    const val stopMetronome = "Stop metronome"

    // Beat cells: "Click, playing".
    const val playing = "playing"

    // Sound picker.
    const val custom = "Custom"
    const val replace = "Replace"
    const val replaceCustomSound = "Replace custom sound"
    const val empty = "empty"
    const val none = "none"

    // Messages (contracts/click-sounds.md).
    const val notWav = "That file isn't a WAV file Tembra can read."
    const val silent = "That file has no sound in it."
    const val tooLarge = "That file is too large (64 MB at most)."
    const val importFailed = "The file couldn't be read."
    const val customSoundMissing = "Your custom sound is missing, so Classic Wood is playing."
    const val focusDenied = "Another app is using the audio right now."
    const val deviceUnavailable = "The audio output isn't available right now."

    fun taps(count: Int): String = "$count taps"

    fun caption(caption: TapCaption): String = when (caption) {
        TapCaption.Rest -> tapRest
        TapCaption.Again -> tapAgain
        is TapCaption.Counting -> taps(caption.taps)
    }

    /** "124 beats per minute". */
    fun beatsPerMinute(tempo: Tempo): String = "${tempo.bpm} beats per minute"

    /**
     * The tap button's state, announced as it changes: the caption, and once taps have set the tempo (two or more)
     * the tempo they set: "3 taps, 124 beats per minute" (spec FR-030).
     */
    fun tapState(tapCaption: TapCaption, tempo: Tempo): String = when (tapCaption) {
        TapCaption.Rest, TapCaption.Again -> caption(tapCaption)
        is TapCaption.Counting -> "${caption(tapCaption)}, ${beatsPerMinute(tempo)}"
    }

    /** The dial's state: "120 beats per minute, Moderato". */
    fun dialState(tempo: Tempo, marking: TempoMarking): String = "${beatsPerMinute(tempo)}, ${marking.label}"

    /** A cell's name: "Beat 3" for the 0-based index 2. */
    fun beat(index: Int): String = "Beat ${index + 1}"

    /** A cell's drawn label: "ACCENT". */
    fun levelLabel(level: BeatLevel): String = level.label.uppercase()

    fun clickSound(name: String): String = "Click sound, $name"

    /** The compact signature menu's name: "Time signature, 4/4". */
    fun timeSignatureOf(label: String): String = "$timeSignature, $label"

    /** The landscape style menu's name: "Style, Standard", or "Style, none" when the cells match no style. */
    fun styleOf(label: String?): String = "$style, ${label ?: none}"

    fun customEntry(name: String?): String = "$custom, ${name ?: empty}"

    fun of(message: MetronomeMessage): String = when (message) {
        MetronomeMessage.NotWav -> notWav
        MetronomeMessage.Silent -> silent
        MetronomeMessage.TooLarge -> tooLarge
        MetronomeMessage.ImportFailed -> importFailed
        MetronomeMessage.CustomSoundMissing -> customSoundMissing
        MetronomeMessage.FocusDenied -> focusDenied
        MetronomeMessage.DeviceUnavailable -> deviceUnavailable
    }
}
