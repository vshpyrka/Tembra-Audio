package audio.tembra.ui.tuner

import audio.tembra.engine.PitchSnapshot

/** Telemetry the UI tests script: every read copies [snapshot]. */
internal class ScriptedTelemetry(val snapshot: PitchSnapshot = PitchSnapshot()) : PitchTelemetry {
    override fun read(into: PitchSnapshot) = into.copyFrom(snapshot)
}
