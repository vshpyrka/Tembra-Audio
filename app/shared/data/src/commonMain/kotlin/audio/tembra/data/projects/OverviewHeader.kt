package audio.tembra.data.projects

/** The 32-byte header of a `.peaks` file: enough to report progress without reading the buckets. */
data class OverviewHeader(val channels: Int, val frames: Long, val completedBaseBuckets: Long) {
    val totalBaseBuckets: Long get() = (frames + WaveformOverview.BASE_BUCKET - 1) / WaveformOverview.BASE_BUCKET

    val isComplete: Boolean get() = completedBaseBuckets >= totalBaseBuckets

    val completedFraction: Float
        get() = if (totalBaseBuckets == 0L) 1f else (completedBaseBuckets.toFloat() / totalBaseBuckets).coerceIn(0f, 1f)
}
