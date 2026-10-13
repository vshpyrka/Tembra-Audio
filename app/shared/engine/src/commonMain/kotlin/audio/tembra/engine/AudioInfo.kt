package audio.tembra.engine

import audio.tembra.core.project.AudioFormat

/** What `tembra_audio_inspect` reports about a file (contracts/engine-c-abi-v6.md): the facts an `AudioAsset` keeps. */
data class AudioInfo(
    val format: AudioFormat,
    val channels: Int,
    val sampleRate: Int,
    val frames: Long,
) {
    val durationSeconds: Double get() = frames.toDouble() / sampleRate
}
