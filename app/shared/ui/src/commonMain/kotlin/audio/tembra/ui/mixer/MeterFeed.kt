package audio.tembra.ui.mixer

import androidx.compose.runtime.LongState
import audio.tembra.engine.MixSnapshot
import audio.tembra.ui.session.InputTelemetry

/**
 * What the strips' meters draw from (feature 012, research R6), owned by the sheet: the shared [levels] snapshot the
 * sheet root fills once per frame, the frame [tick] every meter reads in draw, the 10 Hz [mirror] for the semantics
 * and the capture telemetry an armed strip's meter follows instead (spec FR-014).
 */
internal class MeterFeed(
    val levels: MixSnapshot,
    val tick: LongState,
    val mirror: MixerLevelMirror,
    val inputTelemetry: InputTelemetry,
)
