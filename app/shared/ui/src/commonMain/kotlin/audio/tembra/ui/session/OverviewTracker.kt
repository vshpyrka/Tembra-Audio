package audio.tembra.ui.session

import audio.tembra.core.project.AudioAssetId
import audio.tembra.core.project.ProjectId
import audio.tembra.data.projects.OverviewStatus
import audio.tembra.data.projects.ProjectRepository
import audio.tembra.data.projects.WaveformOverview
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Follows each asset's overview from the repository's status flow (contracts/project-session.md; spec FR-029): every
 * `Building` emission re-reads the partial `.peaks` so the clips fill in progressively, `Ready` reads it once more and
 * ends the poll, `Failed` ends it without a waveform. An overview the cache lost is rebuilt once. The session owns
 * [scope]; one `overviewPolling` launch per tracked asset, cancelled by [stop] and [clear].
 */
class OverviewTracker(
    private val repository: ProjectRepository,
    private val reader: OverviewReader,
    private val ioDispatcher: CoroutineDispatcher,
    private val scope: CoroutineScope,
    private val onChange: (AudioAssetId, OverviewStatus, WaveformOverview?) -> Unit,
) {
    private val jobs = HashMap<AudioAssetId, Job>()

    fun track(projectId: ProjectId, assetId: AudioAssetId, overviewPath: String) {
        jobs.remove(assetId)?.cancel()
        jobs[assetId] = scope.launch(CoroutineName("overviewPolling")) { poll(projectId, assetId, overviewPath) }
    }

    fun stop(assetId: AudioAssetId) {
        jobs.remove(assetId)?.cancel()
    }

    fun clear() {
        for (job in jobs.values) job.cancel()
        jobs.clear()
    }

    private suspend fun poll(projectId: ProjectId, assetId: AudioAssetId, path: String) {
        var rebuildTried = false
        repository.overviewStatus(projectId, assetId)
            .transformWhile { status ->
                emit(status)
                status is OverviewStatus.Building
            }
            .collect { status ->
                var overview = if (status == OverviewStatus.Failed) null else read(path)
                if (overview == null && status != OverviewStatus.Failed && !rebuildTried) {
                    rebuildTried = true
                    if (repository.rebuildOverview(projectId, assetId)) overview = read(path)
                }
                onChange(assetId, status, overview)
            }
    }

    private suspend fun read(path: String): WaveformOverview? = withContext(ioDispatcher) { reader.read(path) }
}
