package audio.tembra.data.projects

import audio.tembra.engine.PeakBuckets

/**
 * The growing take's waveform (research R7; contracts/take-storage.md "LiveOverview"): the `.peaks` base buckets
 * (256 frames, int8 min and max per channel) as the engine's recorder publishes them through
 * `TimelineAudio.readPeaks`, with the 1024, 4096 and 16 384 levels derived as they arrive. Each coarser bucket is the
 * min and max of the four finer ones; because min and max fold exactly, a partial coarse bucket folded one base
 * bucket at a time equals the bucket recomputed from scratch, so the levels match what the engine's
 * `tembra_audio_overview_build` writes for the same buckets. A gap in the indices (the ring was overrun) repeats the
 * previous bucket; buckets already held are skipped. [asOverview] returns an immutable snapshot in the shape
 * `ClipPainter.drawClip` draws: the base level shares the backing array (its completed prefix never changes; growth
 * reallocates), the coarse levels are copied so their partial buckets stay as they were. The arrays grow a minute of
 * 48 kHz audio at a time, so a 30-minute stereo take holds under 2 MB. One thread appends and reads: the session's
 * 10 Hz poll on the main thread.
 */
class LiveOverview(val channels: Int) {

    init {
        require(channels in 1..MAX_CHANNELS) { "A live overview holds one or two channels, not $channels" }
    }

    private val stride = channels * 2
    private val factors = WaveformOverview.LEVEL_SIZES.map { it / WaveformOverview.BASE_BUCKET }
    private val data: Array<ByteArray> = Array(factors.size) { ByteArray(0) }
    private var baseCount = 0
    private val lastMin = ByteArray(channels)
    private val lastMax = ByteArray(channels)

    /** The index the next poll should ask `readPeaks` for: the number of base buckets held. */
    val nextIndex: Long get() = baseCount.toLong()

    /** Bytes held by the backing arrays, for the memory bound. */
    val allocatedBytes: Long get() = data.sumOf { it.size.toLong() }

    /** Folds the polled [buckets] in; a gap before [PeakBuckets.firstIndex] repeats the previous bucket, an overlap is skipped. */
    fun append(buckets: PeakBuckets) {
        val first = buckets.firstIndex
        var missing = first - nextIndex
        while (missing > 0) {
            appendBucket(lastMin, lastMax, 0)
            missing--
        }
        val skip = if (first < nextIndex) (nextIndex - first).toInt() else 0
        for (bucket in skip until buckets.count) appendBucket(buckets.min, buckets.max, bucket * 2)
    }

    /** An immutable snapshot of every level so far, complete by construction (`completedFraction` = 1). */
    fun asOverview(): WaveformOverview {
        val count = baseCount
        val levels = WaveformOverview.LEVEL_SIZES.mapIndexed { level, size ->
            val buckets = (count + factors[level] - 1) / factors[level]
            val bytes = if (level == 0) data[0] else data[level].copyOf(buckets * stride)
            WaveformLevel(framesPerBucket = size, bucketCount = buckets, completedBuckets = buckets, channels = channels, data = bytes)
        }
        return WaveformOverview(channels, count.toLong() * WaveformOverview.BASE_BUCKET, count.toLong(), levels)
    }

    private fun appendBucket(min: ByteArray, max: ByteArray, offset: Int) {
        val index = baseCount
        ensureCapacity(index + 1)
        for (level in factors.indices) {
            val factor = factors[level]
            val fresh = index % factor == 0
            val at = (index / factor) * stride
            val target = data[level]
            for (channel in 0 until channels) {
                val low = min[offset + channel]
                val high = max[offset + channel]
                val minAt = at + channel * 2
                val maxAt = minAt + 1
                if (fresh) {
                    target[minAt] = low
                    target[maxAt] = high
                } else {
                    if (low < target[minAt]) target[minAt] = low
                    if (high > target[maxAt]) target[maxAt] = high
                }
            }
        }
        for (channel in 0 until channels) {
            lastMin[channel] = min[offset + channel]
            lastMax[channel] = max[offset + channel]
        }
        baseCount = index + 1
    }

    private fun ensureCapacity(baseBuckets: Int) {
        if (data[0].size >= baseBuckets * stride) return
        val capacity = ((baseBuckets + GROWTH_BUCKETS - 1) / GROWTH_BUCKETS) * GROWTH_BUCKETS
        for (level in factors.indices) {
            val buckets = (capacity + factors[level] - 1) / factors[level]
            data[level] = data[level].copyOf(buckets * stride)
        }
    }

    private companion object {
        /** One minute of 48 kHz audio in base buckets: the growth step that keeps a 30-minute take under 2 MB. */
        const val GROWTH_BUCKETS = 11_250

        /** [PeakBuckets] lays out two channels per bucket: the input is mono or stereo. */
        const val MAX_CHANNELS = 2
    }
}
