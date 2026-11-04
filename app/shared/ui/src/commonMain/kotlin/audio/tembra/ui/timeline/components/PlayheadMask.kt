package audio.tembra.ui.timeline.components

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect

/**
 * Where the playhead line must not run (feature 011): the expanded layout's inline track settings, which span the lanes
 * under the open track. The track list reports the row's bounds in root coordinates as it lays it out; the overlay reads
 * them in its draw phase only, so a scroll of the list redraws the line without recomposing anything.
 */
@Stable
internal class PlayheadMask {
    /** The row's bounds in root coordinates, or null while no track's settings are open inline. */
    var hole: Rect? by mutableStateOf(null)
}
