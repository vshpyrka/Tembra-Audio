package audio.tembra.data.projects

import audio.tembra.core.metronome.Tempo
import audio.tembra.core.project.AudioAssetId
import audio.tembra.core.project.ClipId
import audio.tembra.core.project.IdGenerator
import audio.tembra.core.project.Project
import audio.tembra.core.project.ProjectDocument
import audio.tembra.core.project.ProjectId
import audio.tembra.core.project.TrackId
import audio.tembra.core.project.lengthSeconds
import audio.tembra.core.recording.TakeStopReason
import audio.tembra.data.AppDirectories
import audio.tembra.engine.TakeInfo
import audio.tembra.engine.TimelineAudio
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import kotlin.time.Clock

/**
 * [ProjectRepository] over Okio and [AppDirectories] (contracts/project-storage.md; research R9). Every write stages
 * a temporary file beside its target and renames it; writes serialise on one mutex and run on [ioDispatcher].
 * [library] is `version.map { load }`: a state flow of a change counter mapped to a fresh read, so each collector
 * gets the index on subscription and once per change (conflated, never replayed stale); the index is rebuilt from
 * the folders when missing, unreadable, when the folder set differs, or when a `project.json` is newer than it.
 * Overview build failures are remembered in memory for [overviewStatus] until a rebuild succeeds. Staged takes
 * (contracts/take-storage.md) are dot-files under `audio/`: listed and deleted under the same mutex, adopted by the
 * [TakeAdopter] like an import.
 */
class OkioProjectRepository(
    directories: AppDirectories,
    private val audio: TimelineAudio,
    demoAudio: DemoAudioSource,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val codec: ProjectDocumentCodec = ProjectDocumentCodec(),
) : ProjectRepository {

    private val folders = ProjectFolders(directories)
    private val importer = AudioImporter(fileSystem, audio, ioDispatcher)
    private val adopter = TakeAdopter(fileSystem, audio, ioDispatcher)
    private val demoInstaller = DemoProjectInstaller(fileSystem, demoAudio, ioDispatcher)
    private val writes = Mutex()
    private val version = MutableStateFlow(0L)
    private val failedOverviews = MutableStateFlow<Set<Pair<ProjectId, AudioAssetId>>>(emptySet())

    override fun library(): Flow<LibraryIndex> = version.map { loadIndex() }

    override suspend fun create(name: String): Project = io {
        val project = Project.new(IdGenerator.projectId(), name)
        val folder = folders.folder(project.id)
        writeDocument(folder, project)
        updateIndex { index -> index.withEntry(entryOf(project, folder, ProjectOrigin.User, clock())) }
        project
    }

    override suspend fun open(id: ProjectId): OpenResult = withContext(ioDispatcher) {
        val folder = folders.folder(id)
        val text = readTextOrNull(folder.document) ?: return@withContext OpenResult.Missing(id)
        when (val result = codec.decode(text)) {
            is DecodeResult.Decoded -> OpenResult.Opened(opened(result.document.project, folder))
            is DecodeResult.Malformed, is DecodeResult.Invalid, is DocumentTooNew -> OpenResult.Unavailable(id, result.failureReason().orEmpty())
        }
    }

    override suspend fun save(project: Project, retiredAssets: Set<AudioAssetId>) {
        io {
            val folder = folders.folder(project.id)
            writeDocument(folder, project)
            for (assetId in retiredAssets) {
                listAudioFiles(folder).filter { it.name.startsWith(assetId.sha256 + ".") }.forEach { fileSystem.delete(it) }
                fileSystem.delete(folder.overviewPath(assetId))
            }
            updateIndex { index ->
                val previous = index.entry(project.id)
                index.withEntry(entryOf(project, folder, previous?.origin ?: ProjectOrigin.User, clock()))
            }
        }
    }

    override suspend fun duplicate(id: ProjectId): Project? = io {
        val source = folders.folder(id)
        val text = readTextOrNull(source.document) ?: return@io null
        val original = (codec.decode(text) as? DecodeResult.Decoded)?.document?.project ?: return@io null
        val copy = reidentified(original, IdGenerator.projectId())
        val target = folders.folder(copy.id)
        fileSystem.createDirectories(target.audio)
        fileSystem.createDirectories(target.cacheRoot)
        for ((assetId, asset) in original.assets) {
            val format = asset.format ?: continue
            copyIfPresent(source.audioPath(assetId, format), target.audioPath(assetId, format))
            copyIfPresent(source.overviewPath(assetId), target.overviewPath(assetId))
        }
        copyIfPresent(source.view, target.view)
        var named = copy
        updateIndex { index ->
            named = copy.copy(name = copyName(index, original.name))
            writeDocument(target, named)
            index.withEntry(entryOf(named, target, ProjectOrigin.User, clock()))
        }
        named
    }

    override suspend fun delete(id: ProjectId) {
        io {
            val folder = folders.folder(id)
            fileSystem.deleteRecursively(folder.root)
            fileSystem.deleteRecursively(folder.cacheRoot)
            updateIndex { index ->
                index.copy(entries = index.entries.filterNot { it.id == id }, openProjectId = index.openProjectId?.takeUnless { it == id })
            }
        }
    }

    override suspend fun installDemo(): Project {
        val existing = io {
            val index = readIndexOrRebuild()
            val demo = index.entries.firstOrNull { it.origin == ProjectOrigin.Demo } ?: return@io null
            val text = readTextOrNull(folders.folder(demo.id).document) ?: return@io null
            (codec.decode(text) as? DecodeResult.Decoded)?.document?.project
        }
        return existing ?: restoreDemo()
    }

    override suspend fun restoreDemo(): Project {
        val previous = io {
            val index = readIndexOrRebuild()
            index.entries.filter { it.origin == ProjectOrigin.Demo }.map { it.id to (index.openProjectId == it.id) }
        }
        for ((id, _) in previous) delete(id)
        val wasOpen = previous.any { it.second }
        val folder = folders.folder(IdGenerator.projectId())
        val project = demoInstaller.install(folder)
        val installed = io {
            writeDocument(folder, project)
            fileSystem.createDirectories(folder.cacheRoot)
            for ((assetId, asset) in project.assets) {
                val format = asset.format ?: continue
                buildOverview(project.id, folder.audioPath(assetId, format), folder.overviewPath(assetId), assetId)
            }
            updateIndex { index ->
                index.withEntry(entryOf(project, folder, ProjectOrigin.Demo, clock()))
                    .copy(openProjectId = if (wasOpen) project.id else index.openProjectId)
            }
            project
        }
        return installed
    }

    override suspend fun importAudio(projectId: ProjectId, file: PickedFile): ImportResult {
        val result = importer.import(folders.folder(projectId), file)
        if (result is ImportResult.Accepted) markOverview(projectId, result.assetId, built = result.overviewBuilt)
        return result
    }

    override suspend fun sizeOnDevice(id: ProjectId): Long = withContext(ioDispatcher) { sizeOf(folders.folder(id)) }

    override suspend fun setOpen(id: ProjectId?) {
        io { updateIndex { it.copy(openProjectId = id) } }
    }

    override suspend fun viewState(id: ProjectId): ProjectViewState = withContext(ioDispatcher) {
        val text = readTextOrNull(folders.folder(id).view) ?: return@withContext ProjectViewState()
        try {
            codec.json.decodeFromString(ProjectViewState.serializer(), text)
        } catch (failure: SerializationException) {
            ProjectViewState()
        } catch (failure: IllegalArgumentException) {
            ProjectViewState()
        }
    }

    override suspend fun saveViewState(id: ProjectId, state: ProjectViewState) = withContext(ioDispatcher) {
        val folder = folders.folder(id)
        if (!fileSystem.exists(folder.root)) return@withContext
        writeStaged(folder.viewStaging, folder.view, codec.json.encodeToString(ProjectViewState.serializer(), state) + "\n")
    }

    override suspend fun rebuildOverview(projectId: ProjectId, assetId: AudioAssetId): Boolean = withContext(ioDispatcher) {
        val folder = folders.folder(projectId)
        val audioFile = listAudioFiles(folder).firstOrNull { it.name.startsWith(assetId.sha256 + ".") } ?: return@withContext false
        fileSystem.createDirectories(folder.cacheRoot)
        buildOverview(projectId, audioFile, folder.overviewPath(assetId), assetId)
    }

    override fun overviewStatus(projectId: ProjectId, assetId: AudioAssetId): Flow<OverviewStatus> = flow {
        while (true) {
            val status = withContext(ioDispatcher) { statusOf(projectId, assetId) }
            emit(status)
            when (status) {
                is OverviewStatus.Building -> delay(OVERVIEW_POLL_MS)
                OverviewStatus.Ready, OverviewStatus.Failed -> return@flow
            }
        }
    }.distinctUntilChanged()

    // --- Staged takes (feature 010, contracts/take-storage.md) ---------------------------------------------------------

    override fun audioDirectory(id: ProjectId): String = folders.folder(id).audio.toString()

    override suspend fun stageTake(id: ProjectId, manifest: TakeManifest): String = locked {
        val folder = folders.folder(id)
        fileSystem.createDirectories(folder.audio)
        fileSystem.write(folder.takeManifestPath(manifest.token)) { writeUtf8(manifest.toJson()) }
        folder.takeStagingPath(manifest.token).toString()
    }

    override suspend fun adoptTake(id: ProjectId, manifest: TakeManifest, takeInfo: TakeInfo): AdoptedTake {
        val result = adopter.adopt(folders.folder(id), manifest, takeInfo)
        if (result is AdoptedTake.Adopted) {
            // The adopter builds the overview of a new copy; a copy that already existed keeps the one it had.
            val built = withContext(ioDispatcher) { WaveformOverviewReader.readHeader(fileSystem, result.overviewPath.toPath()) != null }
            markOverview(id, result.assetId, built)
        }
        return result
    }

    override suspend fun adoptPendingTake(id: ProjectId, manifest: TakeManifest): AdoptedTake {
        val folder = folders.folder(id)
        val info = withContext(ioDispatcher) { audio.inspect(folder.takeStagingPath(manifest.token).toString()) }
        if (info == null) {
            discardTake(id, manifest.token)
            return AdoptedTake.Discarded
        }
        // Shorter than 0.01 s reads as no frames: the adopter then deletes both files, as it does for a cancelled take.
        val frames = if (info.durationSeconds < MIN_TAKE_SECONDS) 0L else info.frames
        val takeInfo = TakeInfo(
            startFrames = 0,
            startBeats = manifest.startBeats.value,
            frames = frames,
            channels = info.channels,
            sampleRate = info.sampleRate,
            alignmentFrames = manifest.alignmentFrames,
            stopReason = TakeStopReason.WriteFailed,
        )
        return adoptTake(id, manifest, takeInfo)
    }

    override suspend fun pendingTakes(id: ProjectId): List<TakeManifest> = locked {
        val folder = folders.folder(id)
        listTakeManifests(folder).mapNotNull { (token, path) ->
            val manifest = readTextOrNull(path)?.let { TakeManifest.parse(it) }?.takeIf { it.token == token }
            val wav = folder.takeStagingPath(token)
            val readable = manifest != null && fileSystem.exists(wav) && audio.inspect(wav.toString()) != null
            if (readable) {
                manifest
            } else {
                // Nothing can recover a take without a readable file: both go (contracts/take-storage.md "Crash recovery").
                fileSystem.delete(path)
                fileSystem.delete(wav)
                null
            }
        }.sortedBy { it.startedAt }
    }

    override suspend fun discardTake(id: ProjectId, token: String) {
        locked {
            val folder = folders.folder(id)
            fileSystem.delete(folder.takeStagingPath(token))
            fileSystem.delete(folder.takeManifestPath(token))
        }
    }

    /** Every take manifest under `audio/` as (token, path). */
    private fun listTakeManifests(folder: ProjectFolder): List<Pair<String, Path>> =
        if (fileSystem.exists(folder.audio)) fileSystem.list(folder.audio).mapNotNull { path -> folder.takeTokenOf(path)?.let { it to path } } else emptyList()

    // --- Index -------------------------------------------------------------------------------------------------------

    private suspend fun loadIndex(): LibraryIndex = locked { readIndexOrRebuild() }

    /** Runs [block] under the write mutex on the IO dispatcher: file work that leaves the index as it is. */
    private suspend fun <T> locked(block: suspend () -> T): T = withContext(ioDispatcher) { writes.withLock { block() } }

    /** [locked], then announces the change to [library] collectors. */
    private suspend fun <T> io(block: suspend () -> T): T {
        val result = locked(block)
        version.update { it + 1 }
        return result
    }

    /** Must run under [writes]. */
    private suspend fun updateIndex(transform: suspend (LibraryIndex) -> LibraryIndex): LibraryIndex {
        val next = transform(readIndexOrRebuild())
        writeIndex(next)
        return next
    }

    private suspend fun readIndexOrRebuild(): LibraryIndex {
        val stored = readIndex()
        if (stored != null && !isStale(stored)) return stored
        return rebuild(stored)
    }

    private fun readIndex(): LibraryIndex? {
        val text = readTextOrNull(folders.libraryIndex) ?: return null
        return try {
            codec.json.decodeFromString(LibraryIndex.serializer(), text)
        } catch (failure: SerializationException) {
            null
        } catch (failure: IllegalArgumentException) {
            null
        }
    }

    private fun isStale(index: LibraryIndex): Boolean {
        val ids = folders.projectIds(fileSystem)
        if (ids.toSet() != index.entries.map { it.id }.toSet()) return true
        val indexTime = modifiedAt(folders.libraryIndex) ?: return true
        return ids.any { (modifiedAt(folders.folder(it).document) ?: Long.MAX_VALUE) > indexTime }
    }

    private suspend fun rebuild(previous: LibraryIndex?): LibraryIndex {
        val ids = folders.projectIds(fileSystem)
        val entries = ids.map { id -> rebuiltEntry(id, previous?.entry(id)) }
        val index = LibraryIndex(openProjectId = previous?.openProjectId?.takeIf { it in ids }, entries = entries)
        writeIndex(index)
        return index
    }

    private suspend fun rebuiltEntry(id: ProjectId, previous: LibraryEntry?): LibraryEntry {
        val folder = folders.folder(id)
        val text = readTextOrNull(folder.document).orEmpty()
        val lastEdited = modifiedAt(folder.document) ?: clock()
        val origin = previous?.origin ?: ProjectOrigin.User
        return when (val result = codec.decode(text)) {
            is DecodeResult.Decoded -> entryOf(result.document.project, folder, origin, lastEdited)
            is DecodeResult.Malformed, is DecodeResult.Invalid, is DocumentTooNew -> LibraryEntry(
                id = id,
                name = rawName(text) ?: previous?.name ?: id.value,
                description = previous?.description.orEmpty(),
                key = previous?.key,
                tempo = previous?.tempo ?: Tempo.Default,
                lengthSeconds = previous?.lengthSeconds ?: 0.0,
                trackCount = previous?.trackCount ?: 0,
                byteSize = sizeOf(folder),
                lastEdited = lastEdited,
                origin = origin,
                overview = previous?.overview ?: CardOverview.Empty,
                unavailable = result.failureReason(),
            )
        }
    }

    /** The project name from a document that failed to decode, when the text still carries one. */
    private fun rawName(text: String): String? = try {
        val project = (codec.json.parseToJsonElement(text) as? JsonObject)?.get("project") as? JsonObject
        (project?.get("name") as? JsonPrimitive)?.contentOrNull
    } catch (failure: SerializationException) {
        null
    }

    private suspend fun entryOf(project: Project, folder: ProjectFolder, origin: ProjectOrigin, lastEdited: Long): LibraryEntry = LibraryEntry(
        id = project.id,
        name = project.name,
        description = project.description,
        key = project.key,
        tempo = project.tempo,
        lengthSeconds = project.lengthSeconds,
        trackCount = project.tracks.size,
        byteSize = sizeOf(folder),
        lastEdited = lastEdited,
        origin = origin,
        overview = CardOverviewBuilder.build(project, readOverviews(project, folder)),
    )

    private fun readOverviews(project: Project, folder: ProjectFolder): Map<AudioAssetId, WaveformOverview> =
        project.assets.keys.mapNotNull { id -> WaveformOverviewReader.read(fileSystem, folder.overviewPath(id))?.let { id to it } }.toMap()

    private fun writeIndex(index: LibraryIndex) {
        fileSystem.createDirectories(folders.root)
        writeStaged(folders.libraryIndexStaging, folders.libraryIndex, codec.json.encodeToString(LibraryIndex.serializer(), index) + "\n")
    }

    /** "<name> copy", then "<name> copy 2", "<name> copy 3" while the name is taken. */
    private fun copyName(index: LibraryIndex, name: String): String {
        val taken = index.entries.map { it.name }.toSet()
        val base = "$name copy"
        if (base !in taken) return base
        var n = 2
        while ("$base $n" in taken) n++
        return "$base $n"
    }

    // --- Documents and files -----------------------------------------------------------------------------------------

    private fun writeDocument(folder: ProjectFolder, project: Project) {
        fileSystem.createDirectories(folder.root)
        writeStaged(folder.documentStaging, folder.document, codec.encode(ProjectDocument(project = project)))
    }

    private fun writeStaged(staging: Path, target: Path, text: String) {
        fileSystem.write(staging) { writeUtf8(text) }
        fileSystem.delete(target)
        fileSystem.atomicMove(staging, target)
    }

    private fun opened(project: Project, folder: ProjectFolder): OpenedProject {
        val audioPaths = HashMap<AudioAssetId, String>()
        val overviewPaths = HashMap<AudioAssetId, String>()
        val missing = HashSet<AudioAssetId>()
        for ((assetId, asset) in project.assets) {
            overviewPaths[assetId] = folder.overviewPath(assetId).toString()
            val format = asset.format
            val path = format?.let { folder.audioPath(assetId, it) }
            if (path != null && fileSystem.exists(path)) audioPaths[assetId] = path.toString() else missing += assetId
        }
        return OpenedProject(project, audioPaths, overviewPaths, missing)
    }

    /** New project, track and clip ids; the same asset ids (contracts/project-document.md rule 4). */
    private fun reidentified(project: Project, id: ProjectId): Project = project.copy(
        id = id,
        tracks = project.tracks.map { track ->
            track.copy(id = TrackId(IdGenerator.trackId().value), clips = track.clips.map { it.copy(id = ClipId(IdGenerator.clipId().value)) }.toPersistentList())
        }.toPersistentList(),
    )

    private suspend fun buildOverview(projectId: ProjectId, audioPath: Path, overviewPath: Path, assetId: AudioAssetId): Boolean {
        val built = audio.buildOverview(audioPath.toString(), overviewPath.toString())
        markOverview(projectId, assetId, built)
        return built
    }

    private fun markOverview(projectId: ProjectId, assetId: AudioAssetId, built: Boolean) {
        failedOverviews.update { if (built) it - (projectId to assetId) else it + (projectId to assetId) }
    }

    private fun statusOf(projectId: ProjectId, assetId: AudioAssetId): OverviewStatus {
        if ((projectId to assetId) in failedOverviews.value) return OverviewStatus.Failed
        val header = WaveformOverviewReader.readHeader(fileSystem, folders.folder(projectId).overviewPath(assetId)) ?: return OverviewStatus.Building(0f)
        return if (header.isComplete) OverviewStatus.Ready else OverviewStatus.Building(header.completedFraction)
    }

    private fun sizeOf(folder: ProjectFolder): Long {
        val document = fileSystem.metadataOrNull(folder.document)?.size ?: return 0L
        return document + listAudioFiles(folder).sumOf { fileSystem.metadataOrNull(it)?.size ?: 0L }
    }

    private fun listAudioFiles(folder: ProjectFolder): List<Path> =
        if (fileSystem.exists(folder.audio)) fileSystem.list(folder.audio).filterNot { it.name.startsWith(".") } else emptyList()

    private fun copyIfPresent(from: Path, to: Path) {
        if (fileSystem.exists(from)) fileSystem.copy(from, to)
    }

    private fun readTextOrNull(path: Path): String? = try {
        if (fileSystem.exists(path)) fileSystem.read(path) { readUtf8() } else null
    } catch (failure: IOException) {
        null
    }

    private fun modifiedAt(path: Path): Long? = fileSystem.metadataOrNull(path)?.lastModifiedAtMillis

    private companion object {
        const val OVERVIEW_POLL_MS = 500L

        /** A recovered take shorter than this is discarded (contracts/take-storage.md "Crash recovery"). */
        const val MIN_TAKE_SECONDS = 0.01
    }
}
