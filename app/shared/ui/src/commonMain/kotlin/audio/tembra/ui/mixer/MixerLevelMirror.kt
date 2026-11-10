package audio.tembra.ui.mixer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import audio.tembra.engine.CaptureSnapshot
import audio.tembra.engine.MixSnapshot
import audio.tembra.ui.designsystem.MeterLevel
import audio.tembra.ui.session.InputTelemetry
import audio.tembra.ui.timeline.LevelHold
import kotlinx.coroutines.delay
import kotlin.math.max

/**
 * The sheet's 10 Hz mirror of the meters for the semantics (feature 012, research R6; data-model.md §4): one
 * [MeterLevel] state per strip and one for the master, refreshed from the shared snapshot the sheet root fills each
 * frame (so it adds no engine read of its own), the armed strip's from the capture telemetry. Each state changes only
 * when its value does, so a meter recomposes for its announcement at most ten times a second and the strip never.
 */
internal class MixerLevelMirror {
    private val strips = List(MixSnapshot.TRACKS) { mutableStateOf(MeterLevel.Silent) }
    private val masterState = mutableStateOf(MeterLevel.Silent)
    private val capture = CaptureSnapshot()

    fun strip(index: Int): State<MeterLevel> = strips[index]

    val master: State<MeterLevel> get() = masterState

    /** One step: every strip's level from [levels] (strip [armedIndex] from [input]), then the master's. */
    fun update(levels: MixSnapshot, armedIndex: Int?, input: InputTelemetry, frameNanos: Long) {
        for (index in strips.indices) {
            val next = when {
                index == armedIndex -> {
                    input.read(frameNanos, capture)
                    MeterLevel(peakDb = LevelHold.dbOf(max(capture.peak[0], capture.peak[1])), rmsDb = MixSnapshot.SILENCE_DB, clipped = capture.clipped)
                }
                index < levels.trackCount -> MeterLevel(
                    peakDb = LevelHold.dbOf(max(levels.peak[index * 2], levels.peak[index * 2 + 1])),
                    rmsDb = max(levels.rmsDb(index, 0), levels.rmsDb(index, 1)),
                    clipped = levels.clipped(index),
                )
                else -> MeterLevel.Silent
            }
            if (strips[index].value != next) strips[index].value = next
        }
        val master = MeterLevel(
            peakDb = LevelHold.dbOf(max(levels.masterPeak[0], levels.masterPeak[1])),
            rmsDb = max(levels.masterRmsDb(0), levels.masterRmsDb(1)),
            clipped = levels.masterClipped,
        )
        if (masterState.value != master) masterState.value = master
    }

    /** Everything back to silence (the sheet closed). */
    fun clear() {
        for (state in strips) if (state.value != MeterLevel.Silent) state.value = MeterLevel.Silent
        if (masterState.value != MeterLevel.Silent) masterState.value = MeterLevel.Silent
    }
}

/**
 * The mirror for one sheet, stepping at 10 Hz while [active] (the `InputMeter` pattern of feature 010): the one
 * `LaunchedEffect` the meters add, keyed so an armed track's change restarts it on the new index.
 */
@Composable
internal fun rememberMixerLevelMirror(levels: MixSnapshot, active: Boolean, armedIndex: Int?, inputTelemetry: InputTelemetry): MixerLevelMirror {
    val mirror = remember { MixerLevelMirror() }
    LaunchedEffect(active, armedIndex, inputTelemetry) {
        if (!active) {
            mirror.clear()
            return@LaunchedEffect
        }
        while (true) {
            val frameNanos = withFrameNanos { it }
            mirror.update(levels, armedIndex, inputTelemetry, frameNanos)
            delay(MIRROR_MILLIS)
        }
    }
    return mirror
}

private const val MIRROR_MILLIS = 100L
