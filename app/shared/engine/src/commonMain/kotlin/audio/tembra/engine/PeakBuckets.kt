package audio.tembra.engine

/**
 * A mutable buffer of live peak buckets (`tembra_peak_bucket`, research R7): the `.peaks` base level of 256 frames,
 * int8 min and max per channel, laid out as `[bucket * 2 + channel]`. [TimelineAudio.readPeaks] fills [count]
 * buckets from the ring and sets [firstIndex] (the index of bucket 0 here) and [nextIndex]; a caller that asked for
 * an index below [firstIndex] missed buckets the ring had already overwritten.
 */
class PeakBuckets(val capacity: Int = DEFAULT_CAPACITY) {
    val min: ByteArray = ByteArray(capacity * 2)
    val max: ByteArray = ByteArray(capacity * 2)
    var count: Int = 0
    var firstIndex: Long = 0L
    var nextIndex: Long = 0L

    fun clear() {
        count = 0
    }

    companion object {
        /** More than a 10 Hz poll ever finds (a 5 ms block rate fills 19 buckets per 100 ms). */
        const val DEFAULT_CAPACITY = 256
    }
}
