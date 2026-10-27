package audio.tembra.data.projects

import audio.tembra.engine.PeakBuckets
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The growing take's overview (research R7; contracts/take-storage.md "LiveOverview"): the levels it derives from the
 * engine's base buckets equal what [WaveformOverviewReader] reads from the engine's own `.peaks` of the same buckets,
 * a ring overrun repeats the previous bucket, every zoom finds its level, each snapshot is immutable, and a
 * 30-minute take stays under 2 MB.
 */
class LiveOverviewTest {

    private val fileSystem = FakeFileSystem()
    private val path = "/cache/live.peaks".toPath()

    /** The per-bucket peak the fixture and the live buckets share: bucket-indexed so both sides see the same values. */
    private fun peakOf(bucket: Long, channel: Int): Int = if (channel == 0) ((bucket * 7) % 128).toInt() else ((bucket / 3) % 100).toInt()

    /** Feeds [count] symmetric ±peak buckets from [first], through polls of at most [capacity] buckets as the session's 10 Hz poll would. */
    private fun LiveOverview.feed(first: Long, count: Int, capacity: Int = PeakBuckets.DEFAULT_CAPACITY) {
        var index = first
        val end = first + count
        while (index < end) {
            val buckets = PeakBuckets(capacity)
            buckets.firstIndex = index
            buckets.count = minOf(capacity.toLong(), end - index).toInt()
            for (i in 0 until buckets.count) {
                for (channel in 0 until channels) {
                    val peak = peakOf(index + i, channel)
                    buckets.min[i * 2 + channel] = (-peak).toByte()
                    buckets.max[i * 2 + channel] = peak.toByte()
                }
            }
            buckets.nextIndex = index + buckets.count
            append(buckets)
            index += buckets.count
        }
    }

    /** One poll of explicit (min, max) pairs per channel per bucket, starting at [first]. */
    private fun poll(first: Long, channels: Int, vararg buckets: List<Pair<Int, Int>>): PeakBuckets {
        val result = PeakBuckets(buckets.size)
        result.firstIndex = first
        result.count = buckets.size
        result.nextIndex = first + buckets.size
        for ((i, bucket) in buckets.withIndex()) {
            for (channel in 0 until channels) {
                result.min[i * 2 + channel] = bucket[channel].first.toByte()
                result.max[i * 2 + channel] = bucket[channel].second.toByte()
            }
        }
        return result
    }

    private fun assertSameOverview(expected: WaveformOverview, actual: WaveformOverview) {
        assertEquals(expected.channels, actual.channels)
        assertEquals(expected.frames, actual.frames)
        assertEquals(expected.completedBaseBuckets, actual.completedBaseBuckets)
        assertEquals(expected.isComplete, actual.isComplete)
        assertEquals(expected.levels.map { it.framesPerBucket }, actual.levels.map { it.framesPerBucket })
        assertEquals(expected.levels.map { it.bucketCount }, actual.levels.map { it.bucketCount })
        assertEquals(expected.levels.map { it.completedBuckets }, actual.levels.map { it.completedBuckets })
        for ((level, expectedLevel) in expected.levels.withIndex()) {
            val actualLevel = actual.levels[level]
            for (bucket in 0 until expectedLevel.bucketCount) {
                for (channel in 0 until expected.channels) {
                    assertEquals(expectedLevel.min(bucket, channel), actualLevel.min(bucket, channel), "min of level $level bucket $bucket channel $channel")
                    assertEquals(expectedLevel.max(bucket, channel), actualLevel.max(bucket, channel), "max of level $level bucket $bucket channel $channel")
                }
            }
        }
    }

    @Test
    fun levelsEqualTheReadersForTheSameBuckets() {
        val channels = 2
        val baseBuckets = 1_000
        fileSystem.createDirectories(path.parent!!)
        fileSystem.write(path) { write(DataFixtures.peaks(channels, baseBuckets * 256L) { frame, channel -> peakOf(frame / 256, channel) }) }
        val expected = assertNotNull(WaveformOverviewReader.read(fileSystem, path))
        assertEquals(listOf(1_000, 250, 63, 16), expected.levels.map { it.bucketCount }, "the last coarse buckets are partial")
        val live = LiveOverview(channels)
        live.feed(0, baseBuckets)
        val actual = live.asOverview()
        assertSameOverview(expected, actual)
        assertEquals(1f, actual.completedFraction)
        assertEquals(baseBuckets.toLong(), live.nextIndex)
    }

    @Test
    fun aMonoTakeMatchesTheReaderToo() {
        val baseBuckets = 70
        fileSystem.createDirectories(path.parent!!)
        fileSystem.write(path) { write(DataFixtures.peaks(1, baseBuckets * 256L) { frame, channel -> peakOf(frame / 256, channel) }) }
        val expected = assertNotNull(WaveformOverviewReader.read(fileSystem, path))
        val live = LiveOverview(1)
        live.feed(0, baseBuckets, capacity = 7)
        assertSameOverview(expected, live.asOverview())
    }

    @Test
    fun coarseBucketsFoldTheMinAndTheMaxSeparately() {
        val live = LiveOverview(2)
        live.append(
            poll(
                0, 2,
                listOf(-10 to 5, -100 to 100), listOf(-3 to 20, 0 to 0), listOf(-7 to 7, -9 to 9), listOf(-1 to 1, -4 to 40),
                listOf(-50 to 2, -6 to 6), listOf(-2 to 60, -8 to 80),
            ),
        )
        val overview = live.asOverview()
        assertEquals(listOf(6, 2, 1, 1), overview.levels.map { it.bucketCount })
        assertEquals(6L * 256, overview.frames)
        val base = overview.levels[0]
        assertEquals(-10, base.min(0, 0))
        assertEquals(5, base.max(0, 0))
        assertEquals(80, base.max(5, 1))
        val quarter = overview.levels[1]
        assertEquals(-10, quarter.min(0, 0))
        assertEquals(20, quarter.max(0, 0))
        assertEquals(-100, quarter.min(0, 1))
        assertEquals(100, quarter.max(0, 1))
        assertEquals(-50, quarter.min(1, 0))
        assertEquals(60, quarter.max(1, 0))
        assertEquals(-8, quarter.min(1, 1))
        assertEquals(80, quarter.max(1, 1))
        for (level in 2..3) {
            assertEquals(-50, overview.levels[level].min(0, 0), "level $level")
            assertEquals(60, overview.levels[level].max(0, 0), "level $level")
            assertEquals(-100, overview.levels[level].min(0, 1), "level $level")
            assertEquals(100, overview.levels[level].max(0, 1), "level $level")
        }
    }

    @Test
    fun aGapRepeatsThePreviousBucketAndAnOverlapIsSkipped() {
        val live = LiveOverview(2)
        live.feed(0, 10)
        live.append(poll(15, 2, listOf(-1 to 1, -2 to 2), listOf(-3 to 3, -4 to 4)))
        assertEquals(17L, live.nextIndex, "the gap of five buckets is filled")
        val overview = live.asOverview()
        val base = overview.levels[0]
        assertEquals(17, base.bucketCount)
        for (bucket in 10..14) {
            for (channel in 0..1) {
                assertEquals(-peakOf(9, channel), base.min(bucket, channel), "bucket $bucket repeats bucket 9")
                assertEquals(peakOf(9, channel), base.max(bucket, channel), "bucket $bucket repeats bucket 9")
            }
        }
        assertEquals(-1, base.min(15, 0))
        assertEquals(4, base.max(16, 1))
        live.append(poll(16, 2, listOf(-9 to 9, -9 to 9), listOf(-5 to 5, -6 to 6), listOf(-7 to 7, -8 to 8)))
        assertEquals(19L, live.nextIndex, "the bucket already held is skipped")
        val grown = live.asOverview().levels[0]
        assertEquals(4, grown.max(16, 1), "bucket 16 keeps its first value")
        assertEquals(-5, grown.min(17, 0))
        assertEquals(8, grown.max(18, 1))
    }

    @Test
    fun aGapBeforeTheFirstBucketIsSilence() {
        val live = LiveOverview(1)
        live.append(poll(3, 1, listOf(-20 to 20)))
        val base = live.asOverview().levels[0]
        assertEquals(4, base.bucketCount)
        for (bucket in 0..2) {
            assertEquals(0, base.min(bucket, 0))
            assertEquals(0, base.max(bucket, 0))
        }
        assertEquals(20, base.max(3, 0))
    }

    @Test
    fun everyZoomFindsItsLevel() {
        val live = LiveOverview(1)
        live.feed(0, 200)
        val overview = live.asOverview()
        assertEquals(256, overview.levelFor(1.0).framesPerBucket)
        assertEquals(256, overview.levelFor(300.0).framesPerBucket)
        assertEquals(1024, overview.levelFor(600.0).framesPerBucket)
        assertEquals(4096, overview.levelFor(3000.0).framesPerBucket)
        assertEquals(16384, overview.levelFor(9000.0).framesPerBucket)
        assertEquals(16384, overview.levelFor(1_000_000.0).framesPerBucket)
        assertEquals(listOf(200, 50, 13, 4), overview.levels.map { it.completedBuckets })
    }

    @Test
    fun anEmptyOverviewDrawsAsSilence() {
        val overview = LiveOverview(2).asOverview()
        assertEquals(0L, overview.frames)
        assertTrue(overview.isComplete)
        assertEquals(1f, overview.completedFraction)
        assertEquals(listOf(0, 0, 0, 0), overview.levels.map { it.bucketCount })
        assertEquals(0, overview.peakBetween(0, 1000))
        assertEquals(256, overview.levelFor(100.0).framesPerBucket)
    }

    @Test
    fun aSnapshotNeverChangesAfterLaterAppends() {
        val live = LiveOverview(2)
        live.feed(0, 10)
        val before = live.asOverview()
        val partialMax = before.levels[1].max(2, 0)
        assertEquals(maxOf(peakOf(8, 0), peakOf(9, 0)), partialMax, "the third quarter bucket holds buckets 8 and 9 so far")
        live.append(poll(10, 2, listOf(-127 to 127, -127 to 127), listOf(-127 to 127, -127 to 127)))
        val after = live.asOverview()
        assertEquals(10, before.levels[0].bucketCount)
        assertEquals(3, before.levels[1].bucketCount)
        assertEquals(partialMax, before.levels[1].max(2, 0), "the earlier snapshot keeps its partial bucket")
        assertEquals(0, before.levels[0].max(10, 0), "the earlier snapshot does not see the new bucket")
        assertEquals(12, after.levels[0].bucketCount)
        assertEquals(127, after.levels[1].max(2, 0))
        assertEquals(127, after.levels[0].max(11, 1))
    }

    @Test
    fun aThirtyMinuteTakeStaysUnderTwoMegabytes() {
        // 30 min × 60 s × 48 000 frames / 256 frames per bucket (the task's "5 625 buckets" is a 30-second take).
        val baseBuckets = 337_500
        val live = LiveOverview(2)
        live.feed(0, baseBuckets)
        assertEquals(baseBuckets.toLong(), live.nextIndex)
        assertTrue(live.allocatedBytes < 2L * 1024 * 1024, "allocated ${live.allocatedBytes} bytes")
        val overview = live.asOverview()
        assertEquals(listOf(337_500, 84_375, 21_094, 5_274), overview.levels.map { it.bucketCount })
        assertEquals(30L * 60 * 48_000, overview.frames)
        assertEquals(peakOf(337_499, 1), overview.levels[0].max(337_499, 1))
    }
}
