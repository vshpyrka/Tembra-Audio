package audio.tembra.core.project

import audio.tembra.core.metronome.BeatUnit
import audio.tembra.core.metronome.TimeSignature
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The snapping grid of the toolbar (spec FR-018): a bar, a quarter, an eighth, a sixteenth note or off. Steps are
 * expressed in beats of the signature's unit, so a quarter note is one beat in 4/4 and two beats in 6/8. Remembered
 * per project on the device (`ProjectViewState`), never in the document.
 */
@Serializable
enum class GridSize(val label: String) {
    @SerialName("bar")
    Bar("Bar"),

    @SerialName("quarter")
    Quarter("1/4"),

    @SerialName("eighth")
    Eighth("1/8"),

    @SerialName("sixteenth")
    Sixteenth("1/16"),

    @SerialName("off")
    Off("Off");

    /** The grid step in beats of [signature]'s unit; null when the grid is off. */
    fun beatsOf(signature: TimeSignature): Beats? {
        val quarter = when (signature.unit) {
            BeatUnit.Quarter -> 1.0
            BeatUnit.Eighth -> 2.0
        }
        return when (this) {
            Bar -> Beats(signature.beats.toDouble())
            Quarter -> Beats(quarter)
            Eighth -> Beats(quarter / 2)
            Sixteenth -> Beats(quarter / 4)
            Off -> null
        }
    }

    companion object {
        val Default: GridSize = Sixteenth
    }
}
