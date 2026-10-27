package audio.tembra.data.projects

import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.core.project.AudioFormat
import audio.tembra.core.project.InstrumentLabel
import audio.tembra.core.project.MusicalKey
import audio.tembra.core.project.Percent
import audio.tembra.core.project.ProjectId
import audio.tembra.core.project.lengthSeconds
import audio.tembra.core.timeline.TimeFormat
import audio.tembra.data.metronome.FakeAppDirectories
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The demo document of spec FR-003 and FR-045 (research R18) and its audio copies. */
class DemoProjectInstallerTest {

    private val fileSystem = FakeFileSystem()
    private val folder = ProjectFolders(FakeAppDirectories()).folder(ProjectId("demo"))
    private val bytes = mapOf("drums" to ByteArray(500) { 1 }, "bass" to ByteArray(500) { 2 }, "keys" to ByteArray(900) { 3 }, "pad" to ByteArray(900) { 4 })

    @Test
    fun theDocumentContent() = runTest {
        val project = DemoProjectInstaller(fileSystem, FakeDemoAudioSource(bytes), StandardTestDispatcher(testScheduler)).install(folder)
        assertEquals("Demo project", project.name)
        assertEquals("Four loops to play with", project.description)
        assertEquals(MusicalKey.DMinor, project.key)
        assertEquals(Tempo(124), project.tempo)
        assertEquals(TimeSignature.FourFour, project.timeSignature)
        assertEquals(Percent(80), project.masterVolume)
        assertEquals(listOf("Drums", "Bass", "Keys", "Pad"), project.tracks.map { it.name })
        assertEquals(listOf(InstrumentLabel.Drums, InstrumentLabel.Bass, InstrumentLabel.Keys, InstrumentLabel.Synth), project.tracks.map { it.label })
        assertEquals(listOf(0, 1, 2, 3), project.tracks.map { it.colorIndex })
        assertEquals(listOf(1, 1, 1, 2), project.tracks.map { it.clips.size })
        val pad = project.tracks[3].clips
        assertEquals(0.0, pad[0].startBeats.value)
        assertEquals(0L, pad[0].offsetFrames)
        assertEquals(743_226L, pad[0].lengthFrames)
        assertEquals(32.0, pad[1].startBeats.value)
        assertEquals(743_226L, pad[1].offsetFrames)
        assertEquals(743_226L, pad[1].lengthFrames)
        assertEquals(listOf("drums", "bass", "keys", "pad", "pad"), project.tracks.flatMap { it.clips }.map { it.name })
        assertEquals(listOf(1, 1, 2, 2), project.assets.values.map { it.channels })
        assertTrue(project.assets.values.all { it.frames == DemoProject.FRAMES && it.sampleRate == 48_000 && it.format == AudioFormat.Wav })
        assertEquals("00:30.96", TimeFormat.clock(project.lengthSeconds))
    }

    @Test
    fun theAudioIsCopiedUnderItsOwnHash() = runTest {
        val project = DemoProjectInstaller(fileSystem, FakeDemoAudioSource(bytes), StandardTestDispatcher(testScheduler)).install(folder)
        for ((name, content) in bytes) {
            val id = DataFixtures.assetId(content)
            val asset = project.assets.getValue(id)
            assertEquals("$name.wav", asset.fileName)
            assertEquals(content.size.toLong(), asset.byteSize)
            assertContentEquals(content, fileSystem.read(folder.audioPath(id, AudioFormat.Wav)) { readByteArray() })
        }
        assertEquals(4, fileSystem.list(folder.audio).size)
        assertTrue(project.tracks.flatMap { it.clips }.all { it.assetId in project.assets })
    }

    @Test
    fun freshIdsEveryInstallAndFixedIdsForTheGolden() = runTest {
        val installer = DemoProjectInstaller(fileSystem, FakeDemoAudioSource(bytes), StandardTestDispatcher(testScheduler))
        val first = installer.install(folder)
        val second = installer.install(ProjectFolders(FakeAppDirectories()).folder(ProjectId("demo2")))
        assertNotEquals(first.id, second.id)
        assertNotEquals(first.tracks[0].id, second.tracks[0].id)
        val fixed = DemoProject.build(DemoProjectIds.fixed())
        assertEquals(ProjectId("0b6f1c9a-4d2e-4f7a-9c1b-2e5d8a7f3c10"), fixed.id)
        assertEquals(DemoProject.BUNDLED_ASSET_IDS.values.toSet(), fixed.assets.keys)
        assertEquals(5_945_852L, fixed.assets.getValue(DemoProject.BUNDLED_ASSET_IDS.getValue("keys")).byteSize)
    }
}
