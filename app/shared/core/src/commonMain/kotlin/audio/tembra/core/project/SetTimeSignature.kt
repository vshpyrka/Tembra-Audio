package audio.tembra.core.project

import audio.tembra.core.metronome.TimeSignature

/** Sets the time signature (spec FR-037); clip positions in beats are untouched. */
data class SetTimeSignature(val timeSignature: TimeSignature) : ProjectEdit
