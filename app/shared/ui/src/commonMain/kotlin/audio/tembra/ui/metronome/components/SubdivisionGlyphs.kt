package audio.tembra.ui.metronome.components

import audio.tembra.core.metronome.BeatUnit
import audio.tembra.core.metronome.Subdivision
import audio.tembra.ui.theme.TembraIcon

/**
 * The note figure of each subdivision on its beat unit (design iteration D1): what one beat holds when it is split
 * into the subdivision's ticks, so a quarter-note beat reads ♩, two eighths, four sixteenths, an eighth triplet, and an
 * eighth-note beat (6/8, 7/8) the same figures a value shorter. The tiles and the chips draw the same glyphs.
 */
internal object SubdivisionGlyphs {
    fun of(subdivision: Subdivision, unit: BeatUnit): TembraIcon = when (unit) {
        BeatUnit.Quarter -> when (subdivision) {
            Subdivision.One -> TembraIcon.NoteQuarter
            Subdivision.Two -> TembraIcon.NoteEighthPair
            Subdivision.Four -> TembraIcon.NoteSixteenthQuad
            Subdivision.Triplet -> TembraIcon.NoteEighthTriplet
        }
        BeatUnit.Eighth -> when (subdivision) {
            Subdivision.One -> TembraIcon.NoteEighth
            Subdivision.Two -> TembraIcon.NoteSixteenthPair
            Subdivision.Four -> TembraIcon.NoteThirtySecondQuad
            Subdivision.Triplet -> TembraIcon.NoteSixteenthTriplet
        }
    }
}
