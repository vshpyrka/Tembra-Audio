package audio.tembra.ui.timeline

import androidx.compose.runtime.Immutable
import audio.tembra.core.project.GainDb
import audio.tembra.core.project.InstrumentLabel
import audio.tembra.core.project.Pan
import audio.tembra.core.project.TrackId
import kotlinx.collections.immutable.ImmutableList

/** One track as the header and lane render it (contracts/timeline-screen.md): the document's fields plus the derived audibility. */
@Immutable
data class TrackUi(
    val id: TrackId,
    val name: String,
    val label: InstrumentLabel?,
    val colorIndex: Int,
    val gainDb: GainDb,
    val muted: Boolean,
    val soloed: Boolean,
    /** From the solo/mute truth table (spec FR-024): drawn dimmed when false. */
    val audible: Boolean,
    val clips: ImmutableList<ClipUi>,
    // Feature 010 (contracts/timeline-recording-ui.md).
    /** The record-arm toggle is lit: the input meter shows in the header (spec FR-001, FR-005). */
    val armed: Boolean = false,
    /** Record sits in this compact header in the label chip's place (bars narrower than 432 dp while armed; research R17). */
    val recordHere: Boolean = false,
    // Feature 012 (contracts/mixer-session.md): the pan the expanded header's readout and the mixer strip show.
    val pan: Pan = Pan.Centre,
) {
    /** "Track 2, Guitar, −2.4dB, L 35, muted, armed" (contracts/timeline-screen.md "Semantics"; FR-029 adds "armed"; feature 012 the pan when not centred). */
    val description: String
        get() = buildList {
            add(name)
            label?.let { add(it.displayName) }
            add(gainDb.readout)
            if (pan != Pan.Centre) add(pan.readout)
            if (muted) add("muted")
            if (soloed) add("solo")
            if (armed) add("armed")
        }.joinToString(", ")
}
