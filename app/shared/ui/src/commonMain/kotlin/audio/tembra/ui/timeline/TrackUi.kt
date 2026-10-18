package audio.tembra.ui.timeline

import androidx.compose.runtime.Immutable
import audio.tembra.core.project.GainDb
import audio.tembra.core.project.InstrumentLabel
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
) {
    /** "Track 2, Guitar, −2.4dB, muted" (contracts/timeline-screen.md "Semantics"). */
    val description: String
        get() = buildList {
            add(name)
            label?.let { add(it.displayName) }
            add(gainDb.readout)
            if (muted) add("muted")
            if (soloed) add("solo")
        }.joinToString(", ")
}
