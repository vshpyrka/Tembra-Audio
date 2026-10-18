package audio.tembra.data.projects

import audio.tembra.core.project.AudioAssetId
import audio.tembra.core.project.Project
import audio.tembra.core.project.ProjectId
import kotlinx.coroutines.flow.Flow

/**
 * The project folders, the library index and the import pipeline (contracts/project-storage.md; spec FR-044 to
 * FR-054). Every member suspends so the caller owns cancellation and failure (`kotlin-concurrency-and-flow`);
 * [library] and [overviewStatus] are cold flows that re-read on subscription. [OkioProjectRepository] is the real
 * one, [FakeProjectRepository] the in-memory one for tests.
 */
interface ProjectRepository {
    /** Emits the index on subscription and after every change; rebuilt from the folders when missing, unreadable or stale. */
    fun library(): Flow<LibraryIndex>

    /** A new folder and document at the defaults (120 BPM, 4/4, empty) plus its index entry. */
    suspend fun create(name: String): Project

    /** Reads and validates the document. */
    suspend fun open(id: ProjectId): OpenResult

    /**
     * Stages `project.json.tmp` then renames, and refreshes the index entry. Audio copies no clip references are
     * deleted only for the ids in [retiredAssets]: the session names them once the history no longer holds them.
     */
    suspend fun save(project: Project, retiredAssets: Set<AudioAssetId> = emptySet())

    /** A complete copy named "<name> copy" (numbered when taken) with new project, track and clip ids; null when [id] cannot be read. */
    suspend fun duplicate(id: ProjectId): Project?

    /** Removes both folders and the index entry; clears the open project when it was this one. */
    suspend fun delete(id: ProjectId)

    /** Writes the demo document and its audio from the bundled resources; returns the existing demo when present. */
    suspend fun installDemo(): Project

    /** Replaces the demo project with a fresh copy (spec FR-045). */
    suspend fun restoreDemo(): Project

    /** The import pipeline for one picked file into [projectId]'s folder. */
    suspend fun importAudio(projectId: ProjectId, file: PickedFile): ImportResult

    /** `project.json` plus every file under `audio`, never the cache (spec FR-048). */
    suspend fun sizeOnDevice(id: ProjectId): Long

    /** Records the open project for the next launch (spec FR-047). */
    suspend fun setOpen(id: ProjectId?)

    /** The device-side zoom and grid; defaults when nothing was saved. */
    suspend fun viewState(id: ProjectId): ProjectViewState

    /** A small direct write of `view.json`, never touching `project.json`. */
    suspend fun saveViewState(id: ProjectId, state: ProjectViewState)

    /** Builds a missing overview from the audio copy (a purged cache, spec FR-029); false when the engine cannot. */
    suspend fun rebuildOverview(projectId: ProjectId, assetId: AudioAssetId): Boolean

    /** Polls the `.peaks` header every 500 ms while building; emits on change and completes at Ready or Failed. */
    fun overviewStatus(projectId: ProjectId, assetId: AudioAssetId): Flow<OverviewStatus>
}
