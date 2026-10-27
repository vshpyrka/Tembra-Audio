package audio.tembra.data.projects

import audio.tembra.core.project.AudioAsset
import audio.tembra.core.project.AudioFormat
import audio.tembra.core.project.Beats
import audio.tembra.core.project.ClipTemplate
import audio.tembra.core.project.ProjectId
import audio.tembra.core.project.TrackId
import audio.tembra.data.metronome.FakeAppDirectories
import audio.tembra.engine.AudioInfo
import audio.tembra.engine.FakeAudioEngine
import audio.tembra.engine.RecordConfig
import audio.tembra.engine.TakeInfo
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Adoption of a staged take (contracts/take-storage.md "TakeAdopter.adopt"): the staging WAV comes from
 * [FakeAudioEngine.takeWriter] as the recorder would leave it, the inspection is scripted per path. The
 * `timeline.waveform` section around the overview build is `PerfTrace.section`, inert outside a measurement build and
 * observable only through the engine module's internal sink, so like [AudioImporterTest] this asserts the build call.
 */
class TakeAdopterTest {

    private val fileSystem = FakeFileSystem()
    private val engine = FakeAudioEngine()
    private val folder = ProjectFolders(FakeAppDirectories()).folder(ProjectId("p1"))
    private val manifest = TakeManifest(
        token = "take-a",
        trackId = TrackId("track-1"),
        startBeats = Beats(4.0),
        takeName = "Take 1",
        startedAt = 1_700_000_000_000L,
        alignmentFrames = 96,
        sampleRate = 48_000,
        channels = 1,
    )
    private val frames = 4_800L
    private val wav = wavBytes(channels = 1, sampleRate = 48_000, frames = frames)
    private val assetId = DataFixtures.assetId(wav)
    private val staging = folder.takeStagingPath(manifest.token)
    private val manifestPath = folder.takeManifestPath(manifest.token)
    private val target = folder.audioPath(assetId, AudioFormat.Wav)

    private fun TestScope.adopter(): TakeAdopter = TakeAdopter(fileSystem, engine, StandardTestDispatcher(testScheduler))

    private fun info(channels: Int = 1, sampleRate: Int = 48_000, frames: Long = this.frames): AudioInfo = AudioInfo(AudioFormat.Wav, channels, sampleRate, frames)

    /**
     * The controller's half (the manifest beside the file) then the engine's half (the staging WAV through the take
     * writer), ended as the engine ends a take; [inspected] is what the inspector reports for the staging file.
     */
    private suspend fun recordTake(manifest: TakeManifest = this.manifest, bytes: ByteArray = wav, inspected: AudioInfo? = info()): TakeInfo {
        fileSystem.createDirectories(folder.audio)
        fileSystem.write(folder.takeManifestPath(manifest.token)) { writeUtf8(manifest.toJson()) }
        engine.takeWriter = { path -> fileSystem.write(path.toPath()) { write(bytes) } }
        engine.inspectResults[folder.takeStagingPath(manifest.token).toString()] = inspected
        engine.openTransport()
        engine.arm(manifest.channels)
        engine.recordStart(RecordConfig.of(folder.takeStagingPath(manifest.token).toString(), trackIndex = 0, countInBeats = 0, sampleRate = manifest.sampleRate))
        return engine.recordStop().copy(frames = frames, alignmentFrames = manifest.alignmentFrames)
    }

    private fun offlineCalls(): List<String> = engine.calls.filter { it.startsWith("inspect(") || it.startsWith("buildOverview(") }

    private fun audioFiles(): List<String> = if (fileSystem.exists(folder.audio)) fileSystem.list(folder.audio).map { it.name } else emptyList()

    @Test
    fun aTakeIsInspectedHashedRenamedAndGivenAnOverview() = runTest {
        val takeInfo = recordTake()
        val result = assertIs<AdoptedTake.Adopted>(adopter().adopt(folder, manifest, takeInfo))
        assertEquals(assetId, result.assetId)
        assertEquals(AudioAsset("${assetId.sha256}.wav", AudioFormat.Wav, 1, 48_000, frames, wav.size.toLong()), result.asset)
        assertEquals(ClipTemplate(assetId, "Take 1", offsetFrames = 0, lengthFrames = frames), result.clip)
        assertEquals(Beats(4.0), result.startBeats)
        assertEquals(target.toString(), result.audioPath)
        assertEquals(folder.overviewPath(assetId).toString(), result.overviewPath)
        assertContentEquals(wav, fileSystem.read(target) { readByteArray() })
        assertFalse(fileSystem.exists(staging), "the staging file was renamed")
        assertFalse(fileSystem.exists(manifestPath), "the manifest is gone after adoption")
        assertEquals(listOf("inspect($staging)", "buildOverview($target)"), offlineCalls(), "inspected in place, then the overview of the copy")
        assertTrue(fileSystem.exists(folder.cacheRoot), "the cache folder exists for the .peaks file")
        assertEquals(listOf(target.name), audioFiles())
    }

    @Test
    fun theOverviewIsWrittenWhereTheClipWillReadIt() = runTest {
        engine.overviewWriter = { path -> fileSystem.write(path.toPath()) { write(DataFixtures.peaks(1, frames) { _, _ -> 40 }) }; true }
        val result = assertIs<AdoptedTake.Adopted>(adopter().adopt(folder, manifest, recordTake()))
        assertEquals(frames, WaveformOverviewReader.readHeader(fileSystem, result.overviewPath.toPath())?.frames)
    }

    @Test
    fun aFailedOverviewBuildStillAdoptsTheTake() = runTest {
        engine.overviewWriter = { false }
        val result = assertIs<AdoptedTake.Adopted>(adopter().adopt(folder, manifest, recordTake()))
        assertFalse(fileSystem.exists(result.overviewPath.toPath()), "no overview: the repository reports it as failed, the clip still exists")
        assertTrue(fileSystem.exists(target))
        assertFalse(fileSystem.exists(manifestPath))
    }

    @Test
    fun aDuplicateHashReusesTheCopyAndBuildsNoOverview() = runTest {
        fileSystem.createDirectories(folder.audio)
        fileSystem.createDirectories(folder.cacheRoot)
        fileSystem.write(target) { write(wav) }
        fileSystem.write(folder.overviewPath(assetId)) { write(DataFixtures.peaks(1, frames) { _, _ -> 40 }) }
        val result = assertIs<AdoptedTake.Adopted>(adopter().adopt(folder, manifest, recordTake()))
        assertEquals(assetId, result.assetId)
        assertEquals(listOf("inspect($staging)"), offlineCalls())
        assertFalse(fileSystem.exists(staging), "the duplicate staging file is deleted")
        assertFalse(fileSystem.exists(manifestPath))
        assertEquals(listOf(target.name), audioFiles())
        assertContentEquals(wav, fileSystem.read(target) { readByteArray() })
    }

    @Test
    fun anInspectionThatDisagreesLeavesTheStagingFilesInPlace() = runTest {
        val takeInfo = recordTake(inspected = info(channels = 2))
        val channels = assertIs<AdoptedTake.Failed>(adopter().adopt(folder, manifest, takeInfo))
        assertTrue("channel" in channels.reason, channels.reason)
        assertTrue(fileSystem.exists(staging), "the file stays for the next launch")
        assertTrue(fileSystem.exists(manifestPath), "the manifest stays for the next launch")
        assertEquals(listOf(staging.name, manifestPath.name).sorted(), audioFiles().sorted(), "nothing was renamed")
        assertEquals(listOf("inspect($staging)"), offlineCalls(), "no overview of a refused take")

        engine.inspectResults[staging.toString()] = info(sampleRate = 44_100)
        val rate = assertIs<AdoptedTake.Failed>(adopter().adopt(folder, manifest, takeInfo))
        assertTrue("rate" in rate.reason, rate.reason)

        engine.inspectResults[staging.toString()] = info(frames = frames + 4_097)
        val length = assertIs<AdoptedTake.Failed>(adopter().adopt(folder, manifest, takeInfo))
        assertTrue("frame" in length.reason, length.reason)
        assertTrue(fileSystem.exists(staging))

        engine.inspectResults[staging.toString()] = info(frames = frames + 4_096)
        val withinABlock = assertIs<AdoptedTake.Adopted>(adopter().adopt(folder, manifest, takeInfo))
        assertEquals(frames + 4_096, withinABlock.asset.frames, "the file's own count is what the asset keeps")
        assertEquals(frames + 4_096, withinABlock.clip.lengthFrames)
        assertFalse(fileSystem.exists(staging))
    }

    @Test
    fun anUnreadableFileFailsAndLeavesTheStagingFiles() = runTest {
        val takeInfo = recordTake(inspected = null)
        assertIs<AdoptedTake.Failed>(adopter().adopt(folder, manifest, takeInfo))
        assertTrue(fileSystem.exists(staging))
        assertTrue(fileSystem.exists(manifestPath))
        assertEquals(listOf("inspect($staging)"), offlineCalls())
    }

    @Test
    fun zeroFramesIsDiscardedAndDeletesBothFiles() = runTest {
        val takeInfo = recordTake().copy(frames = 0)
        assertTrue(takeInfo.discarded)
        assertEquals(AdoptedTake.Discarded, adopter().adopt(folder, manifest, takeInfo))
        assertFalse(fileSystem.exists(staging))
        assertFalse(fileSystem.exists(manifestPath))
        assertTrue(audioFiles().isEmpty())
        assertTrue(offlineCalls().isEmpty(), "nothing is inspected or built for a discarded take")
    }

    @Test
    fun theClipTakesItsNameAndStartFromTheManifest() = runTest {
        val seventh = manifest.copy(token = "take-g", takeName = "Take 7", startBeats = Beats(12.5), trackId = TrackId("track-3"))
        val bytes = wavBytes(channels = 1, sampleRate = 48_000, frames = frames, fill = 0x42)
        val result = assertIs<AdoptedTake.Adopted>(adopter().adopt(folder, seventh, recordTake(manifest = seventh, bytes = bytes)))
        assertEquals("Take 7", result.clip.name)
        assertEquals(Beats(12.5), result.startBeats)
        assertEquals(DataFixtures.assetId(bytes), result.assetId)
        assertEquals(0L, result.clip.offsetFrames)
        assertEquals(frames, result.clip.lengthFrames)
        assertFalse(fileSystem.exists(folder.takeManifestPath("take-g")))
    }

    /** A 24-bit PCM WAV as the recorder writes it: the 44-byte header, then [frames] frames of [fill] bytes. */
    private fun wavBytes(channels: Int, sampleRate: Int, frames: Long, fill: Byte = 0): ByteArray {
        val blockAlign = channels * 3
        val dataBytes = (frames * blockAlign).toInt()
        val buffer = Buffer()
        buffer.writeUtf8("RIFF").writeIntLe(36 + dataBytes).writeUtf8("WAVE")
        buffer.writeUtf8("fmt ").writeIntLe(16).writeShortLe(1).writeShortLe(channels).writeIntLe(sampleRate)
        buffer.writeIntLe(sampleRate * blockAlign).writeShortLe(blockAlign).writeShortLe(24)
        buffer.writeUtf8("data").writeIntLe(dataBytes)
        buffer.write(ByteArray(dataBytes) { fill })
        return buffer.readByteArray()
    }
}
