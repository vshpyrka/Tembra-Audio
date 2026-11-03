package audio.tembra.ui.drop

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import audio.tembra.core.project.Beats

/**
 * One drop zone's state while files hover and land (feature 011, spec FR-026; contracts/drop-import.md behaviour 2–4).
 * [zoneAt] and [beatsAt] map a position local to the zone's layout to the zone under it and the snapped beat there; a
 * null position is a platform that does not say where the drag is (iOS, research R12 follow-up). The platform's drag
 * events reach it through `Modifier.audioDropTarget` as plain values, so it is tested without one.
 */
@Stable
class AudioDropState(
    private val zoneAt: (Offset?) -> DropZone,
    private val beatsAt: (Offset?) -> Beats,
) {
    /** What a drop would do where the files hover now; null when nothing hovers. */
    var hover: DropHover? by mutableStateOf(null)
        private set

    private var audioCount: Int? = null

    /** Files entered the zone at [position]; [audioCount] is how many audio items they hold, null when not told. */
    fun entered(position: Offset?, audioCount: Int?) {
        this.audioCount = audioCount
        moved(position)
    }

    fun moved(position: Offset?) {
        val zone = zoneAt(position)
        val next = DropHover(zone, DropPlanner.preview(zone, audioCount, beatsAt(position)))
        if (next != hover) hover = next
    }

    fun exited() {
        hover = null
    }

    fun ended() {
        hover = null
        audioCount = null
    }

    /** The files landed at [position]: plans what [read] returns there. The zone and beat are taken before reading. */
    suspend fun dropped(position: Offset?, read: suspend () -> List<DroppedItem>): DropOutcome {
        val zone = zoneAt(position)
        val beats = beatsAt(position)
        hover = null
        return DropPlanner.plan(zone, read(), beats)
    }
}
