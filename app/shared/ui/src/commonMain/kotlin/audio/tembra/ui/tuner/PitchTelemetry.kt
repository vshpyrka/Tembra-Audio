package audio.tembra.ui.tuner

import audio.tembra.engine.PitchSnapshot

/** Draw-phase access to the latest reading (Constitution II telemetry rule); the meter calls it every frame. */
fun interface PitchTelemetry {
    fun read(into: PitchSnapshot)
}
