package audio.tembra.ui.mixer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LongState
import androidx.compose.runtime.remember
import audio.tembra.engine.MixSnapshot
import audio.tembra.ui.designsystem.rememberFrameTick
import audio.tembra.ui.session.InputTelemetry
import kotlinx.collections.immutable.ImmutableList

/**
 * What the strips' meters draw from (feature 012, research R6), owned by the surface that shows them (the sheet, the window): the shared [levels] snapshot the
 * sheet root fills once per frame, the frame [tick] every meter reads in draw, the 10 Hz [mirror] for the semantics
 * and the capture telemetry an armed strip's meter follows instead (spec FR-014).
 */
internal class MeterFeed(
    val levels: MixSnapshot,
    val tick: LongState,
    val mirror: MixerLevelMirror,
    val inputTelemetry: InputTelemetry,
)

/**
 * The meters' feed of one surface (research R6): one snapshot the surface's root fills per frame, the frame tick while
 * [active], the 10 Hz mirror for the semantics; the armed strip's meter follows the input (spec FR-014).
 */
@Composable
internal fun rememberMeterFeed(active: Boolean, strips: ImmutableList<StripUi>, inputTelemetry: InputTelemetry): MeterFeed {
    val levels = remember { MixSnapshot() }
    val tick = rememberFrameTick(active = active)
    val armedIndex = strips.firstOrNull { it.meterSource == MeterSource.Input }?.index
    val mirror = rememberMixerLevelMirror(levels, active = active, armedIndex = armedIndex, inputTelemetry = inputTelemetry)
    return remember(levels, tick, mirror, inputTelemetry) { MeterFeed(levels, tick, mirror, inputTelemetry) }
}
