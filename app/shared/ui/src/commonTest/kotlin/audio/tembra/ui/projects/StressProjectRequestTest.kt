package audio.tembra.ui.projects

import audio.tembra.core.project.ProjectId
import audio.tembra.data.projects.DemoProject
import audio.tembra.data.projects.FakeDemoAudioSource
import audio.tembra.data.projects.FakeProjectRepository
import audio.tembra.data.projects.PickedFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The stress project's launch argument (feature 012, contracts/perf-instrumentation.md "Journey `stress`"): parsed like
 * the other debug requests; the seeder builds the project once under its fixed id, after the startup, with one picked
 * file per track cycling through the demo's loops, and builds nothing when a launch finds it in the library.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StressProjectRequestTest {

    @Test
    fun parsesACountInRangeAndIgnoresTheRest() {
        assertEquals(StressProjectRequest(24), StressProjectRequest.parse("24"))
        assertEquals(StressProjectRequest(64), StressProjectRequest.parse(" 64 "))
        assertNull(StressProjectRequest.parse(null))
        assertNull(StressProjectRequest.parse(""))
        assertNull(StressProjectRequest.parse("0"))
        assertNull(StressProjectRequest.parse("65"))
        assertNull(StressProjectRequest.parse("many"))
    }

    @Test
    fun theSeederBuildsTheProjectOnceAfterTheStartupWithOneLoopPerTrack() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val repository = FakeProjectRepository()
        val loops = DemoProject.LOOPS.associate { it.name to it.name.encodeToByteArray() }
        val imports = mutableListOf<Pair<ProjectId, List<PickedFile>>>()
        var started = 0
        val seeder = StressProjectSeeder(repository, FakeDemoAudioSource(loops), scope, beforeStart = { started++ }) { id, files -> imports += id to files }
        seeder.seed(StressProjectRequest(6))
        seeder.seed(StressProjectRequest(6))
        runCurrent()
        assertEquals(1, started, "the startup is awaited once")
        val entry = repository.library().first().entry(StressProject.ID)
        assertEquals(StressProject.name(6), entry?.name)
        val (target, files) = imports.single()
        assertEquals(StressProject.ID, target)
        assertEquals(listOf("drums.wav", "bass.wav", "keys.wav", "pad.wav", "drums.wav", "bass.wav"), files.map { it.displayName })
        assertEquals("keys", files[2].open().buffer().readUtf8(), "the file opens the loop it is named after")
        scope.cancel()
    }

    @Test
    fun aLaunchThatFindsTheProjectBuildsNothing() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val repository = FakeProjectRepository()
        repository.create(StressProject.name(24), StressProject.ID)
        val imports = mutableListOf<ProjectId>()
        val seeder = StressProjectSeeder(repository, FakeDemoAudioSource(emptyMap()), scope) { id, _ -> imports += id }
        seeder.seed(StressProjectRequest(24))
        runCurrent()
        assertEquals(emptyList(), imports)
        assertEquals(1, repository.library().first().entries.count { it.id == StressProject.ID })
        scope.cancel()
    }
}
