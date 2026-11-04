package audio.tembra.engine

import audio.tembra.core.recording.TakeStopReason

/**
 * What the engine reports when a take ends (`tembra_take_info`): the transport frame and beat of the first kept
 * sample, the kept frames (0 = discarded, shorter than 0.01 s, the file deleted), the format and the discard applied,
 * and why it ended. [stopReason] is the core type: the engine's codes map onto it in the actuals, with no mirror here.
 */
data class TakeInfo(
    val startFrames: Long,
    val startBeats: Double,
    val frames: Long,
    val channels: Int,
    val sampleRate: Int,
    val alignmentFrames: Long,
    val stopReason: TakeStopReason,
) {
    val discarded: Boolean get() = frames == 0L
}
