package audio.tembra.ui.projects

import audio.tembra.data.projects.FakeProjectRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The debug seed argument (T121): parsed like the other debug requests, applied once. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectsSeedRequestTest {

    @Test
    fun parsesACountInRangeAndIgnoresTheRest() {
        assertEquals(ProjectsSeedRequest(200), ProjectsSeedRequest.parse("200"))
        assertEquals(ProjectsSeedRequest(1), ProjectsSeedRequest.parse(" 1 "))
        assertNull(ProjectsSeedRequest.parse(null))
        assertNull(ProjectsSeedRequest.parse(""))
        assertNull(ProjectsSeedRequest.parse("0"))
        assertNull(ProjectsSeedRequest.parse("1001"))
        assertNull(ProjectsSeedRequest.parse("many"))
    }

    @Test
    fun theSeederCreatesTheProjectsOnce() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val repository = FakeProjectRepository()
        val seeder = ProjectsSeeder(repository, scope)
        seeder.seed(ProjectsSeedRequest(5))
        seeder.seed(ProjectsSeedRequest(5))
        runCurrent()
        val names = repository.library().first().entries.map { it.name }.sorted()
        assertEquals(listOf("Seed project 1", "Seed project 2", "Seed project 3", "Seed project 4", "Seed project 5"), names)
        scope.cancel()
    }
}
