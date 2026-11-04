package audio.tembra.engine

/**
 * What a take needs from the host (`tembra_record_config`): the staging file, the armed track's index in the
 * arrangement, the count-in in beats (0 = none, ignored while playing), the frame cap (30 minutes at the stream rate)
 * and the free-space floor (50 MB) at which the engine ends the take itself.
 */
data class RecordConfig(
    val wavPath: String,
    val trackIndex: Int,
    val countInBeats: Int,
    val maxFrames: Long,
    val minFreeBytes: Long,
) {
    companion object {
        const val MAX_TAKE_SECONDS = 30 * 60
        const val MIN_FREE_BYTES = 50L * 1024 * 1024

        /** The spec's limits for a take at [sampleRate]. */
        fun of(wavPath: String, trackIndex: Int, countInBeats: Int, sampleRate: Int): RecordConfig = RecordConfig(
            wavPath = wavPath,
            trackIndex = trackIndex,
            countInBeats = countInBeats,
            maxFrames = MAX_TAKE_SECONDS.toLong() * sampleRate,
            minFreeBytes = MIN_FREE_BYTES,
        )
    }
}
