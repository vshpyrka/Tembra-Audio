package audio.tembra.data.projects

import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Parsing the engine's `.peaks` format (contracts/engine-c-abi-v6.md) and picking a level for a zoom. */
class WaveformOverviewReaderTest {

    private val fileSystem = FakeFileSystem()
    private val path = "/cache/a.peaks".toPath()

    private fun write(bytes: ByteArray) {
        fileSystem.createDirectories(path.parent!!)
        fileSystem.write(path) { write(bytes) }
    }

    @Test
    fun parsesTheHeaderAndEveryLevel() {
        val frames = 256L * 1024 + 100
        write(DataFixtures.peaks(2, frames, sampleStep = 1) { frame, channel -> if (channel == 0) (frame / 2048).toInt().coerceAtMost(127) else 5 })
        val overview = assertNotNull(WaveformOverviewReader.read(fileSystem, path))
        assertEquals(2, overview.channels)
        assertEquals(frames, overview.frames)
        assertTrue(overview.isComplete)
        assertEquals(listOf(256, 1024, 4096, 16384), overview.levels.map { it.framesPerBucket })
        assertEquals(listOf(1025, 257, 65, 17), overview.levels.map { it.bucketCount })
        assertEquals(listOf(1025, 257, 65, 17), overview.levels.map { it.completedBuckets })
        val base = overview.levels[0]
        assertEquals(0, base.max(0, 0))
        assertEquals(0, base.min(0, 0))
        assertEquals(5, base.max(0, 1))
        assertEquals(-5, base.min(0, 1))
        assertEquals(8, base.max(8 * 8, 0))
        assertEquals(-8, base.min(8 * 8, 0))
        assertEquals(0, overview.levels[1].max(1, 0))
        assertEquals(1, overview.levels[1].max(2, 0))
        assertEquals(3, overview.levels[2].max(1, 0))
        assertEquals(127, overview.levels[3].max(16, 0))
    }

    @Test
    fun aPartialFileExposesOnlyTheCompletedBuckets() {
        val frames = 256L * 1000
        write(DataFixtures.peaks(1, frames, completedBaseBuckets = 300) { _, _ -> 20 })
        val overview = assertNotNull(WaveformOverviewReader.read(fileSystem, path))
        assertFalse(overview.isComplete)
        assertEquals(0.3f, overview.completedFraction)
        assertEquals(listOf(300, 75, 18, 4), overview.levels.map { it.completedBuckets })
        val header = assertNotNull(WaveformOverviewReader.readHeader(fileSystem, path))
        assertEquals(300L, header.completedBaseBuckets)
        assertEquals(1000L, header.totalBaseBuckets)
        assertEquals(0.3f, header.completedFraction)
    }

    @Test
    fun aFileStillBeingWrittenReadsItsMissingBytesAsSilence() {
        val frames = 256L * 40
        write(DataFixtures.peaks(1, frames, completedBaseBuckets = 40, truncateAfterBase = true) { _, _ -> 30 })
        val overview = assertNotNull(WaveformOverviewReader.read(fileSystem, path))
        assertEquals(30, overview.levels[0].max(39, 0))
        assertEquals(0, overview.levels[1].max(0, 0))
        assertEquals(0, overview.levels[3].min(0, 0))
    }

    @Test
    fun levelForPicksTheBucketClosestToOnePixel() {
        write(DataFixtures.peaks(1, 256L * 200) { _, _ -> 1 })
        val overview = assertNotNull(WaveformOverviewReader.read(fileSystem, path))
        assertEquals(256, overview.levelFor(1.0).framesPerBucket)
        assertEquals(256, overview.levelFor(300.0).framesPerBucket)
        assertEquals(1024, overview.levelFor(600.0).framesPerBucket)
        assertEquals(4096, overview.levelFor(3000.0).framesPerBucket)
        assertEquals(16384, overview.levelFor(9000.0).framesPerBucket)
        assertEquals(16384, overview.levelFor(1_000_000.0).framesPerBucket)
    }

    @Test
    fun peakBetweenFramesUsesTheBaseLevel() {
        write(DataFixtures.peaks(2, 256L * 10, sampleStep = 1) { frame, channel -> if (frame in 512 until 768) 100 + channel else 10 })
        val overview = assertNotNull(WaveformOverviewReader.read(fileSystem, path))
        assertEquals(101, overview.peakBetween(0, 2560))
        assertEquals(10, overview.peakBetween(0, 512))
        assertEquals(101, overview.peakBetween(700, 800))
        assertEquals(10, overview.peakBetween(2000, 2560))
        assertEquals(0, overview.peakBetween(3000, 4000))
    }

    @Test
    fun badOrMissingFilesReadAsNull() {
        assertNull(WaveformOverviewReader.read(fileSystem, path))
        assertNull(WaveformOverviewReader.readHeader(fileSystem, path))
        write(byteArrayOf(1, 2, 3))
        assertNull(WaveformOverviewReader.read(fileSystem, path))
        write("NOPE".encodeToByteArray() + ByteArray(28))
        assertNull(WaveformOverviewReader.readHeader(fileSystem, path))
    }
}
