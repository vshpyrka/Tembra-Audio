package audio.tembra.data.projects

import kotlin.math.abs
import kotlin.math.ln

/**
 * The engine's `.peaks` overview of one audio copy in memory (research R10; contracts/engine-c-abi-v6.md): int8
 * min/max buckets at 256, 1024, 4096 and 16 384 frames per bucket. [levelFor] picks the level whose bucket is closest
 * to one device pixel at the current zoom; a partial file (the builder still running) exposes only its completed
 * buckets.
 */
class WaveformOverview(
    val channels: Int,
    val frames: Long,
    val completedBaseBuckets: Long,
    val levels: List<WaveformLevel>,
) {
    val totalBaseBuckets: Long get() = (frames + BASE_BUCKET - 1) / BASE_BUCKET

    val isComplete: Boolean get() = completedBaseBuckets >= totalBaseBuckets

    val completedFraction: Float
        get() = if (totalBaseBuckets == 0L) 1f else (completedBaseBuckets.toFloat() / totalBaseBuckets).coerceIn(0f, 1f)

    /** The level whose bucket size is nearest [framesPerPixel] on a log scale. */
    fun levelFor(framesPerPixel: Double): WaveformLevel {
        val target = framesPerPixel.coerceAtLeast(1e-9)
        return levels.minBy { abs(ln(it.framesPerBucket / target)) }
    }

    /** The peak 0..127 over the base buckets covering `[startFrame, endFrame)`; silence outside the completed audio. */
    fun peakBetween(startFrame: Long, endFrame: Long): Int {
        val base = levels.firstOrNull() ?: return 0
        if (endFrame <= startFrame) return 0
        val first = (startFrame / BASE_BUCKET).coerceAtLeast(0L).toInt()
        val last = ((endFrame - 1) / BASE_BUCKET).coerceAtMost((base.completedBuckets - 1).toLong()).toInt()
        var peak = 0
        for (bucket in first..last) peak = maxOf(peak, base.peak(bucket))
        return peak
    }

    companion object {
        const val BASE_BUCKET = 256

        /** Frames per bucket of each level, base first. */
        val LEVEL_SIZES: List<Int> = listOf(256, 1024, 4096, 16384)
    }
}
