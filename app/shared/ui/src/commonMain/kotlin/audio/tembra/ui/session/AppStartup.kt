package audio.tembra.ui.session

import audio.tembra.data.projects.ProjectRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * What happens once per process when the UI first composes (spec FR-002, FR-047; contracts/project-session.md): the
 * session starts its observers, the demo is installed on first launch, and the most recently opened project (or the
 * demo) opens. One `startup` launch on the session's scope, so an activity recreated mid-way never cancels it.
 */
@Inject
@SingleIn(AppScope::class)
class AppStartup(
    private val session: ProjectSession,
    private val repository: ProjectRepository,
    @Named("session") private val scope: CoroutineScope,
) {
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch(CoroutineName("startup")) { run() }
    }

    private suspend fun run() {
        session.start()
        val index = repository.library().first()
        val last = index.openProjectId?.takeIf { index.entry(it) != null }
        val target = last ?: repository.installDemo().id
        session.open(target)
    }
}
