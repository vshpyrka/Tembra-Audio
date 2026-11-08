package audio.tembra.data.projects

import audio.tembra.core.project.AudioAsset
import kotlinx.coroutines.CompletableDeferred
import audio.tembra.core.project.AudioAssetId
import audio.tembra.core.project.AudioFormat
import audio.tembra.core.project.ClipId
import audio.tembra.core.project.ClipTemplate
import audio.tembra.core.project.IdGenerator
import audio.tembra.core.project.Project
import audio.tembra.core.project.ProjectId
import audio.tembra.core.project.TrackId
import audio.tembra.core.project.lengthSeconds
import audio.tembra.core.recording.TakeStopReason
import audio.tembra.engine.TakeInfo
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import okio.ByteString.Companion.encodeUtf8

/**
 * In-memory [ProjectRepository] for tests, beside the interface so `:shared:ui` tests can use it: documents in a
 * map, the index as a `StateFlow`, scripted import results, overview statuses and take adoptions, and a [calls] log.
 * Audio paths are `/fake/<projectId>/audio/<sha256>.<ext>`; assets listed in [missingAssets] open as missing. A
 * staged take's path is `/fake/<projectId>/audio/.take-<token>.wav`.
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

    /** Every manifest [stageTake] received, in order. */
    val stagedTakes: MutableList<TakeManifest> = mutableListOf()

    /** The manifests staged and not yet adopted or discarded, per project. */
    val staged: MutableMap<ProjectId, MutableList<TakeManifest>> = mutableMapOf()

    /** Every adoption ([adoptTake] and [adoptPendingTake]) with the take info it received, in order. */
    val adoptedTakes: MutableList<Pair<TakeManifest, TakeInfo>> = mutableListOf()

    /** What [pendingTakes] answers per project; a test seeds it, [discardTake] and the adoptions remove from it. */
    val pending: MutableMap<ProjectId, List<TakeManifest>> = mutableMapOf()

    /**
     * Scripts what an adoption answers; null answers [AdoptedTake.Discarded] for a take without frames and otherwise an
     * [AdoptedTake.Adopted] whose asset is a WAV with the take info's channels, rate and frames, its id derived from
     * the token, the clip named and placed after the manifest.
     */
    var adoptResult: ((TakeManifest, TakeInfo) -> AdoptedTake)? = null

    override fun library(): Flow<LibraryIndex> = index

    override suspend fun create(name: String, id: ProjectId): Project {
        calls += "create($name)"
        val project = Project.new(id, name)
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

    // --- Staged takes (feature 010, contracts/take-storage.md) ---------------------------------------------------------

    override fun audioDirectory(id: ProjectId): String = "/fake/${id.value}/audio"

    override suspend fun stageTake(id: ProjectId, manifest: TakeManifest): String {
        calls += "stageTake(${id.value}, ${manifest.token})"
        stagedTakes += manifest
        staged.getOrPut(id) { mutableListOf() } += manifest
        return "${audioDirectory(id)}/${ProjectFolder.TAKE_PREFIX}${manifest.token}.wav"
    }

    override suspend fun adoptTake(id: ProjectId, manifest: TakeManifest, takeInfo: TakeInfo): AdoptedTake {
        calls += "adoptTake(${id.value}, ${manifest.token}, frames=${takeInfo.frames})"
        return adopt(id, manifest, takeInfo)
    }

    override suspend fun adoptPendingTake(id: ProjectId, manifest: TakeManifest): AdoptedTake {
        calls += "adoptPendingTake(${id.value}, ${manifest.token})"
        val takeInfo = TakeInfo(
            startFrames = 0,
            startBeats = manifest.startBeats.value,
            frames = 2L * manifest.sampleRate,
            channels = manifest.channels,
            sampleRate = manifest.sampleRate,
            alignmentFrames = manifest.alignmentFrames,
            stopReason = TakeStopReason.WriteFailed,
        )
        return adopt(id, manifest, takeInfo)
    }

    override suspend fun pendingTakes(id: ProjectId): List<TakeManifest> {
        calls += "pendingTakes(${id.value})"
        return pending[id].orEmpty().sortedBy { it.startedAt }
    }

    override suspend fun discardTake(id: ProjectId, token: String) {
        calls += "discardTake(${id.value}, $token)"
        forget(id, token)
    }

    private fun adopt(id: ProjectId, manifest: TakeManifest, takeInfo: TakeInfo): AdoptedTake {
        adoptedTakes += manifest to takeInfo
        val result = adoptResult?.invoke(manifest, takeInfo) ?: defaultAdoption(id, manifest, takeInfo)
        when (result) {
            is AdoptedTake.Adopted, AdoptedTake.Discarded -> forget(id, manifest.token)
            is AdoptedTake.Failed -> Unit
        }
        return result
    }

    private fun forget(id: ProjectId, token: String) {
        staged[id]?.removeAll { it.token == token }
        pending[id]?.let { pending[id] = it.filterNot { manifest -> manifest.token == token } }
    }

    private fun defaultAdoption(id: ProjectId, manifest: TakeManifest, takeInfo: TakeInfo): AdoptedTake {
        if (takeInfo.frames == 0L) return AdoptedTake.Discarded
        val assetId = AudioAssetId(manifest.token.encodeUtf8().sha256().hex())
        val asset = AudioAsset(
            fileName = "${assetId.sha256}.wav",
            format = AudioFormat.Wav,
            channels = takeInfo.channels,
            sampleRate = takeInfo.sampleRate,
            frames = takeInfo.frames,
            byteSize = WAV_HEADER_BYTES + takeInfo.frames * takeInfo.channels * BYTES_PER_SAMPLE,
        )
        return AdoptedTake.Adopted(
            assetId = assetId,
            asset = asset,
            clip = ClipTemplate(assetId = assetId, name = manifest.takeName, offsetFrames = 0, lengthFrames = takeInfo.frames),
            startBeats = manifest.startBeats,
            audioPath = "${audioDirectory(id)}/${asset.fileName}",
            overviewPath = "/fake/cache/${id.value}/${assetId.sha256}.peaks",
        )
    }

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

    private companion object {
        const val WAV_HEADER_BYTES = 44L
        const val BYTES_PER_SAMPLE = 3L
    }
}
