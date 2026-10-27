package audio.tembra.data.projects

import audio.tembra.core.project.AudioFormat
import audio.tembra.core.project.ProjectId
import audio.tembra.data.metronome.FakeAppDirectories
import audio.tembra.engine.AudioInfo
import audio.tembra.engine.FakeAudioEngine
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.IOException
import okio.Source
import okio.Timeout
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The import pipeline of contracts/project-storage.md with a scripted inspector (spec FR-044). */
class AudioImporterTest {

    private val fileSystem = FakeFileSystem()
    private val engine = FakeAudioEngine()
    private val folder = ProjectFolders(FakeAppDirectories()).folder(ProjectId("p1"))
    private val bytes = ByteArray(10_000) { (it * 31).toByte() }
    private val assetId = DataFixtures.assetId(bytes)

    private fun TestScope.importer(maxBytes: Long = AudioImporter.DEFAULT_MAX_BYTES): AudioImporter =
        AudioImporter(fileSystem, engine, StandardTestDispatcher(testScheduler), maxBytes)

    private fun file(name: String, content: ByteArray = bytes): PickedFile = PickedFile(name) { Buffer().write(content) }

    private fun stagingFiles(): List<String> =
        if (fileSystem.exists(folder.audio)) fileSystem.list(folder.audio).map { it.name }.filter { it.startsWith(".staging") } else emptyList()

    private fun info(format: AudioFormat, channels: Int = 2, frames: Long = 48_000) = AudioInfo(format, channels, 48_000, frames)

    @Test
    fun aWavIsCopiedHashedInspectedAndPlaced() = runTest {
        val result = assertIs<ImportResult.Accepted>(importer().import(folder, file("Take 01.wav")))
        assertEquals(assetId, result.assetId)
        assertEquals("Take 01.wav", result.asset.fileName)
        assertEquals(AudioFormat.Wav, result.asset.format)
        assertEquals(2, result.asset.channels)
        assertEquals(48_000, result.asset.sampleRate)
        assertEquals(48_000L, result.asset.frames)
        assertEquals(bytes.size.toLong(), result.asset.byteSize)
        assertEquals("Take 01", result.clip.name)
        assertEquals(0L, result.clip.offsetFrames)
        assertEquals(48_000L, result.clip.lengthFrames)
        val stored = folder.audioPath(assetId, AudioFormat.Wav)
        assertContentEquals(bytes, fileSystem.read(stored) { readByteArray() })
        assertEquals(listOf("inspect(${folder.stagingAudioPath(assetId.sha256)})", "buildOverview($stored)"), engine.calls)
        assertTrue(stagingFiles().isEmpty())
    }

    @Test
    fun flacAndMp3AreAcceptedUnderTheirOwnExtension() = runTest {
        engine.inspectResults[folder.stagingAudioPath(assetId.sha256).toString()] = info(AudioFormat.Flac)
        val flac = assertIs<ImportResult.Accepted>(importer().import(folder, file("tone.flac")))
        assertTrue(fileSystem.exists(folder.audioPath(flac.assetId, AudioFormat.Flac)))
        val mp3Bytes = ByteArray(5_000) { (it * 7).toByte() }
        engine.inspectResults[folder.stagingAudioPath(DataFixtures.assetId(mp3Bytes).sha256).toString()] = info(AudioFormat.Mp3, channels = 1)
        val mp3 = assertIs<ImportResult.Accepted>(importer().import(folder, file("tone.MP3", mp3Bytes)))
        assertTrue(fileSystem.exists(folder.audioPath(mp3.assetId, AudioFormat.Mp3)))
        assertEquals(1, mp3.asset.channels)
    }

    @Test
    fun refusalsOfTheMatrix() = runTest {
        engine.defaultInspectResult = info(AudioFormat.Wav, channels = 4)
        assertEquals(ImportResult.Refused("quad.wav", ImportRefusal.TooManyChannels), importer().import(folder, file("quad.wav")))
        engine.defaultInspectResult = info(AudioFormat.Wav, frames = 48_000L * 60 * 31)
        assertEquals(ImportResult.Refused("long.wav", ImportRefusal.TooLong), importer().import(folder, file("long.wav")))
        engine.defaultInspectResult = info(AudioFormat.Wav, frames = 48_000L * 1800)
        assertIs<ImportResult.Accepted>(importer().import(folder, file("exactly30.wav")))
        engine.defaultInspectResult = null
        assertEquals(ImportResult.Refused("broken.wav", ImportRefusal.Unreadable), importer().import(folder, file("broken.wav")))
        assertTrue(stagingFiles().isEmpty())
        assertEquals(listOf(folder.audioPath(assetId, AudioFormat.Wav)), fileSystem.list(folder.audio))
    }

    @Test
    fun anUnsupportedExtensionIsRefusedBeforeAnythingIsCopied() = runTest {
        assertEquals(ImportResult.Refused("song.ogg", ImportRefusal.UnsupportedFormat), importer().import(folder, file("song.ogg")))
        assertEquals(ImportResult.Refused("noext", ImportRefusal.UnsupportedFormat), importer().import(folder, file("noext")))
        assertTrue(engine.calls.isEmpty())
        assertTrue(stagingFiles().isEmpty())
    }

    @Test
    fun anOversizedSourceIsRefusedBeforeDecoding() = runTest {
        val limit = 1_000_000L
        val result = importer(maxBytes = limit).import(folder, PickedFile("huge.wav") { ZeroSource(limit * 600) })
        assertEquals(ImportResult.Refused("huge.wav", ImportRefusal.TooLarge), result)
        assertTrue(engine.calls.isEmpty())
        assertTrue(stagingFiles().isEmpty())
        assertIs<ImportResult.Accepted>(importer(maxBytes = limit).import(folder, PickedFile("fits.wav") { ZeroSource(limit) }))
    }

    @Test
    fun theSameBytesTwiceAreOneAssetWithOneOverviewBuild() = runTest {
        val first = assertIs<ImportResult.Accepted>(importer().import(folder, file("a.wav")))
        val second = assertIs<ImportResult.Accepted>(importer().import(folder, file("b.wav")))
        assertEquals(first.assetId, second.assetId)
        assertEquals("b", second.clip.name)
        assertEquals(1, fileSystem.list(folder.audio).size)
        assertEquals(1, engine.calls.count { it.startsWith("buildOverview(") })
        assertEquals(2, engine.calls.count { it.startsWith("inspect(") })
        assertTrue(stagingFiles().isEmpty())
    }

    @Test
    fun aMultiplePickReturnsEveryResultInOrderAndKeepsGoingPastARefusal() = runTest {
        val quad = ByteArray(3_000) { 9 }
        engine.inspectResults[folder.stagingAudioPath(DataFixtures.assetId(quad).sha256).toString()] = info(AudioFormat.Wav, channels = 4)
        val results = importer().importAll(folder, listOf(file("one.wav"), file("quad.wav", quad), file("three.wav", ByteArray(100) { 5 })))
        assertEquals(3, results.size)
        assertIs<ImportResult.Accepted>(results[0])
        assertEquals(ImportResult.Refused("quad.wav", ImportRefusal.TooManyChannels), results[1])
        assertIs<ImportResult.Accepted>(results[2])
        assertEquals(2, fileSystem.list(folder.audio).size)
        assertTrue(stagingFiles().isEmpty())
    }

    @Test
    fun aFailingSourceIsUnreadableAndLeavesNoStagingFile() = runTest {
        val result = importer().import(folder, PickedFile("gone.wav") { throw IOException("revoked") })
        assertEquals(ImportResult.Refused("gone.wav", ImportRefusal.Unreadable), result)
        val midway = importer().import(folder, PickedFile("mid.wav") { FailingSource(after = 4_000) })
        assertEquals(ImportResult.Refused("mid.wav", ImportRefusal.Unreadable), midway)
        assertTrue(stagingFiles().isEmpty())
        assertTrue(engine.calls.isEmpty())
    }

    /** A source of [size] zero bytes, produced lazily. */
    private class ZeroSource(private val size: Long) : Source {
        private var produced = 0L

        override fun read(sink: Buffer, byteCount: Long): Long {
            if (produced >= size) return -1
            val count = minOf(byteCount, size - produced)
            sink.write(ByteArray(count.toInt()))
            produced += count
            return count
        }

        override fun timeout(): Timeout = Timeout.NONE

        override fun close() = Unit
    }

    /** Yields [after] bytes, then fails like a revoked content grant. */
    private class FailingSource(private val after: Long) : Source {
        private var produced = 0L

        override fun read(sink: Buffer, byteCount: Long): Long {
            if (produced >= after) throw IOException("stream closed")
            val count = minOf(byteCount, after - produced)
            sink.write(ByteArray(count.toInt()) { 1 })
            produced += count
            return count
        }

        override fun timeout(): Timeout = Timeout.NONE

        override fun close() = Unit
    }
}
