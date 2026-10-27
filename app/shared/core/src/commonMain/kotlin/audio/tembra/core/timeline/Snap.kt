package audio.tembra.core.timeline

import audio.tembra.core.metronome.TimeSignature
import audio.tembra.core.project.Beats
import audio.tembra.core.project.GridSize
import kotlin.math.round

/** Snapping to the nearest grid line (spec FR-018): identity when the grid is off; never before zero. */
object Snap {
    fun snap(beats: Beats, grid: GridSize, signature: TimeSignature): Beats {
        val step = grid.beatsOf(signature)?.value ?: return beats.coerceAtLeastZero()
        return Beats(round(beats.value / step) * step).coerceAtLeastZero()
    }
}
