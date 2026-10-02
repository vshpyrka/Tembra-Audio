package audio.tembra.engine

import audio.tembra.core.metronome.BeatLevel
import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TickGrid

/**
 * One bar of ticks as the engine plays it: `tembra_metronome_grid` (data-model.md §6). The engine validates it
 * (`ticksPerBeat` 1–4, at most 32 levels, a whole number of beats) and refuses anything else with `TEMBRA_ERR_ARGUMENT`.
 */
data class MetronomeGrid(val tempo: Tempo, val ticksPerBeat: Int, val levels: List<ClickLevel>) {
    companion object {
        fun from(grid: TickGrid, tempo: Tempo): MetronomeGrid = MetronomeGrid(
            tempo = tempo,
            ticksPerBeat = grid.ticksPerBeat,
            levels = grid.levels.map { level ->
                when (level) {
                    BeatLevel.Accent -> ClickLevel.Accent
                    BeatLevel.Click -> ClickLevel.Click
                    BeatLevel.Sub -> ClickLevel.Sub
                }
            },
        )
    }
}
