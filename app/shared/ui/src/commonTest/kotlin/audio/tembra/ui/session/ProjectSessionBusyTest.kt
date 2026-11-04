package audio.tembra.ui.session

import audio.tembra.core.project.AudioAsset
import audio.tembra.core.project.AudioAssetId
import audio.tembra.core.project.AudioFormat
import audio.tembra.core.project.Beats
import audio.tembra.core.project.ClipTemplate
import audio.tembra.data.metronome.FakeMetronomePreferences
import audio.tembra.data.projects.DemoProject
import audio.tembra.data.projects.DemoProjectIds
import audio.tembra.data.projects.FakeProjectRepository
import audio.tembra.data.projects.ImportRefusal
import audio.tembra.data.projects.ImportResult
import audio.tembra.data.projects.PickedFile
import audio.tembra.data.projects.WaveformOverview
import audio.tembra.engine.FakeAudioEngine
import audio.tembra.ui.platform.FakeBackgroundPlaybackHost
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.Buffer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The busy signal the desktop pointer shows (feature 011, spec FR-015a, research R19): true while an import copies
 * files, false once it has ended, whether it succeeded or every file was refused.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectSessionBusyTest {

    private val dispatcher = StandardTestDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val demo = DemoProject.build(DemoProjectIds.fixed())
    private val repository = FakeProjectRepository(initial = listOf(demo))
    private val engine = FakeAudioEngine()
    private val session = ProjectSession(
        repository = repository,
        audio = engine,
        metronome = engine,
        preferences = FakeMetronomePreferences(),
        host = FakeBackgroundPlaybackHost(),
        scope = scope,
        overviewReader = { WaveformOverview(channels = 1, frames = 512, completedBaseBuckets = 2, levels = emptyList()) },
        ioDispatcher = dispatcher,
    )

    @AfterTest
    fun tearDown() = scope.cancel()

    @Test
    fun busyWhileAnImportCopiesFiles() = runTest(dispatcher) {
        session.start()
        runCurrent()
        session.open(demo.id)
        runCurrent()
        assertFalse(session.busy.value)
        val id = AudioAssetId("ee".repeat(32))
        val asset = AudioAsset("lead.wav", AudioFormat.Wav, channels = 2, sampleRate = 48_000, frames = 48_000, byteSize = 192_044)
        repository.importResults += ImportResult.Accepted(id, asset, ClipTemplate.forAsset(id, asset), true, "/fake/audio/lead.wav", "/fake/cache/lead.peaks")
        val gate = CompletableDeferred<Unit>()
        repository.importGate = gate
        session.importAudio(listOf(PickedFile("lead.wav") { Buffer() }), ImportTarget.NewTracks(Beats.Zero))
        runCurrent()
        assertTrue(session.busy.value, "not busy while the file is being copied")
        gate.complete(Unit)
        runCurrent()
        assertFalse(session.busy.value, "still busy after the import ended")
    }

    @Test
    fun aRefusedImportEndsTheBusyState() = runTest(dispatcher) {
        session.start()
        runCurrent()
        session.open(demo.id)
        runCurrent()
        repository.importResults += ImportResult.Refused("x.ogg", ImportRefusal.UnsupportedFormat)
        val gate = CompletableDeferred<Unit>()
        repository.importGate = gate
        session.importAudio(listOf(PickedFile("x.ogg") { Buffer() }), ImportTarget.NewTracks(Beats.Zero))
        runCurrent()
        assertTrue(session.busy.value)
        gate.complete(Unit)
        runCurrent()
        assertFalse(session.busy.value)
    }
}
