package audio.tembra.data.projects

/**
 * One pyramid level of a [WaveformOverview]: [bucketCount] buckets of [framesPerBucket] frames, each holding an int8
 * min and max per channel in [data] (bucket-major, channel-minor). Only the first [completedBuckets] carry audio
 * while the writer is still running; the rest read as silence.
 */
class WaveformLevel(
    val framesPerBucket: Int,
    val bucketCount: Int,
    val completedBuckets: Int,
    val channels: Int,
    private val data: ByteArray,
) {
    fun min(bucket: Int, channel: Int): Int = if (bucket !in 0 until completedBuckets) 0 else data[index(bucket, channel)].toInt()

    fun max(bucket: Int, channel: Int): Int = if (bucket !in 0 until completedBuckets) 0 else data[index(bucket, channel) + 1].toInt()

    /** The larger of |min| and max over every channel of a bucket, 0..127. */
    fun peak(bucket: Int): Int {
        var peak = 0
        for (channel in 0 until channels) {
            peak = maxOf(peak, max(bucket, channel), -min(bucket, channel))
        }
        return peak.coerceAtMost(127)
    }

    private fun index(bucket: Int, channel: Int): Int = (bucket * channels + channel) * 2
}
