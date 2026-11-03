package audio.tembra.data.projects

import kotlinx.coroutines.CompletableDeferred
import audio.tembra.core.project.AudioAssetId
import audio.tembra.core.project.ClipId
import audio.tembra.core.project.IdGenerator
import audio.tembra.core.project.Project
import audio.tembra.core.project.ProjectId
import audio.tembra.core.project.TrackId
import audio.tembra.core.project.lengthSeconds
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [ProjectRepository] for tests, beside the interface so `:shared:ui` tests can use it: documents in a
 * map, the index as a `StateFlow`, scripted import results and overview statuses, and a [calls] log. Audio paths
 * are `/fake/<projectId>/audio/<sha256>.<ext>`; assets listed in [missingAssets] open as missing.
 */
class FakeProjectRepository(
    initial: List<Project> = emptyList(),
    private val now: () -> Long = { 1_791_331_200_000L },
) : ProjectRepository {

    private val projects = LinkedHashMap<ProjectId, Project>().apply { initial.forEach { put(it.id, it) } }
    private val origins = HashMap<ProjectId, ProjectOrigin>()
    private val viewStates = HashMap<ProjectId, ProjectViewState>()
    private val index = MutableStateFlow(LibraryIndex(entries = initial.map { entryOf(it) }))

    /** Every member called, in order, with its arguments. */
    val calls: MutableList<String> = mutableListOf()

    /** What [importAudio] answers next, in order; empty answers a refusal as unreadable. */
    val importResults: ArrayDeque<ImportResult> = ArrayDeque()

    /** When set, every import waits for it: a copy still in progress (feature 011's busy pointer). */
    var importGate: CompletableDeferred<Unit>? = null

    /** Assets whose file is reported missing by [open]. */
    val missingAssets: MutableSet<AudioAssetId> = mutableSetOf()

    /** The retired assets every [save] received, in order. */
    val retired: MutableList<Set<AudioAssetId>> = mutableListOf()

    /** What [overviewStatus] emits per asset; unlisted assets are ready. */
    val overviewStatuses: MutableStateFlow<Map<AudioAssetId, OverviewStatus>> = MutableStateFlow(emptyMap())

    /** The documents as last saved. */
    val saved: Map<ProjectId, Project> get() = projects

    override fun library(): Flow<LibraryIndex> = index

    override suspend fun create(name: String): Project {
        calls += "create($name)"
        val project = Project.new(IdGenerator.projectId(), name)
        put(project, ProjectOrigin.User)
        return project
    }

    override suspend fun open(id: ProjectId): OpenResult {
        calls += "open(${id.value})"
        val project = projects[id] ?: return OpenResult.Missing(id)
        val audioPaths = project.assets.mapNotNull { (assetId, asset) ->
            val name = asset.storedFileName(assetId)
            if (name == null || assetId in missingAssets) null else assetId to "/fake/${id.value}/audio/$name"
        }.toMap()
        val overviewPaths = project.assets.keys.associateWith { "/fake/cache/${id.value}/${it.sha256}.peaks" }
        val missing = project.assets.keys.filterNot { it in audioPaths }.toSet()
        return OpenResult.Opened(OpenedProject(project, audioPaths, overviewPaths, missing))
    }

    override suspend fun save(project: Project, retiredAssets: Set<AudioAssetId>) {
        calls += "save(${project.id.value}, retired=${retiredAssets.size})"
        retired += retiredAssets
        put(project, origins[project.id] ?: ProjectOrigin.User)
    }

    override suspend fun duplicate(id: ProjectId): Project? {
        calls += "duplicate(${id.value})"
        val original = projects[id] ?: return null
        val names = projects.values.map { it.name }.toSet()
        var name = "${original.name} copy"
        var n = 2
        while (name in names) name = "${original.name} copy ${n++}"
        val copy = original.copy(
            id = IdGenerator.projectId(),
            name = name,
            tracks = original.tracks.map { track ->
                track.copy(id = TrackId(IdGenerator.trackId().value), clips = track.clips.map { it.copy(id = ClipId(IdGenerator.clipId().value)) }.toPersistentList())
            }.toPersistentList(),
        )
        viewStates[original.id]?.let { viewStates[copy.id] = it }
        put(copy, ProjectOrigin.User)
        return copy
    }

    override suspend fun delete(id: ProjectId) {
        calls += "delete(${id.value})"
        projects.remove(id)
        origins.remove(id)
        viewStates.remove(id)
        index.update { current -> current.copy(entries = current.entries.filterNot { it.id == id }, openProjectId = current.openProjectId?.takeUnless { it == id }) }
    }

    override suspend fun installDemo(): Project {
        calls += "installDemo"
        val existing = origins.entries.firstOrNull { it.value == ProjectOrigin.Demo }?.key?.let { projects[it] }
        return existing ?: restoreDemo()
    }

    override suspend fun restoreDemo(): Project {
        calls += "restoreDemo"
        val previous = origins.filterValues { it == ProjectOrigin.Demo }.keys.toList()
        val wasOpen = previous.any { it == index.value.openProjectId }
        previous.forEach { delete(it) }
        val demo = DemoProject.build(DemoProjectIds.random())
        put(demo, ProjectOrigin.Demo)
        if (wasOpen) setOpen(demo.id)
        return demo
    }

    override suspend fun importAudio(projectId: ProjectId, file: PickedFile): ImportResult {
        calls += "importAudio(${projectId.value}, ${file.displayName})"
        importGate?.await()
        return importResults.removeFirstOrNull() ?: ImportResult.Refused(file.displayName, ImportRefusal.Unreadable)
    }

    override suspend fun sizeOnDevice(id: ProjectId): Long = projects[id]?.assets?.values?.sumOf { it.byteSize } ?: 0L

    override suspend fun setOpen(id: ProjectId?) {
        calls += "setOpen(${id?.value})"
        index.update { it.copy(openProjectId = id) }
    }

    override suspend fun viewState(id: ProjectId): ProjectViewState = viewStates[id] ?: ProjectViewState()

    override suspend fun saveViewState(id: ProjectId, state: ProjectViewState) {
        calls += "saveViewState(${id.value})"
        viewStates[id] = state
    }

    override suspend fun rebuildOverview(projectId: ProjectId, assetId: AudioAssetId): Boolean {
        calls += "rebuildOverview(${projectId.value}, ${assetId.sha256.take(8)})"
        overviewStatuses.update { it + (assetId to OverviewStatus.Ready) }
        return true
    }

    override fun overviewStatus(projectId: ProjectId, assetId: AudioAssetId): Flow<OverviewStatus> =
        overviewStatuses.map { it[assetId] ?: OverviewStatus.Ready }

    private fun put(project: Project, origin: ProjectOrigin) {
        projects[project.id] = project
        origins[project.id] = origin
        index.update { current ->
            current.copy(entries = current.entries.filterNot { it.id == project.id } + entryOf(project, origin))
        }
    }

    private fun entryOf(project: Project, origin: ProjectOrigin = ProjectOrigin.User): LibraryEntry = LibraryEntry(
        id = project.id, name = project.name, description = project.description, key = project.key, tempo = project.tempo,
        lengthSeconds = project.lengthSeconds, trackCount = project.tracks.size, byteSize = project.assets.values.sumOf { it.byteSize },
        lastEdited = now(), origin = origin, overview = CardOverview.Empty,
    )
}
