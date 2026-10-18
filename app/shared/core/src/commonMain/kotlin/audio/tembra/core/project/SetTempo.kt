package audio.tembra.core.project

import audio.tembra.core.metronome.Tempo

/** Sets the project tempo (spec FR-036); clip positions in beats are untouched, so their times move. */
data class SetTempo(val tempo: Tempo) : ProjectEdit
