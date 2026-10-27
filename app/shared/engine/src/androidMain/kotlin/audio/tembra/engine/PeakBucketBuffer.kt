package audio.tembra.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The direct buffer the live peak buckets cross JNI in (feature 010, research R7): `tembra_peak_bucket` is four
 * bytes (min[2], max[2]); the buffer holds [capacity] of them and is reused by every read, so a 10 Hz poll allocates
 * nothing. Main thread only, like the other reusable scratch of the engine.
 */
internal class PeakBucketBuffer(val capacity: Int) {
    val buffer: ByteBuffer = ByteBuffer.allocateDirect(capacity * BUCKET_BYTES).order(ByteOrder.nativeOrder())

    /** Copies [count] buckets from the native layout into [into] (min/max per channel at `bucket * 2 + channel`). */
    fun copyInto(into: PeakBuckets, count: Int) {
        val n = minOf(count, into.capacity, capacity)
        for (i in 0 until n) {
            val at = i * BUCKET_BYTES
            into.min[i * 2] = buffer.get(at)
            into.min[i * 2 + 1] = buffer.get(at + 1)
            into.max[i * 2] = buffer.get(at + 2)
            into.max[i * 2 + 1] = buffer.get(at + 3)
        }
        into.count = n
    }

    companion object {
        const val BUCKET_BYTES = 4
    }
}
