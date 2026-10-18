package audio.tembra.data.projects

import audio.tembra.core.metronome.Tempo
import audio.tembra.core.project.AudioAssetId
import audio.tembra.core.project.AudioFormat
import audio.tembra.core.project.GridSize
import audio.tembra.core.project.Project
import audio.tembra.core.project.ProjectDocument
import audio.tembra.core.project.ProjectId
import audio.tembra.core.project.ZoomLevel
import audio.tembra.data.metronome.FakeAppDirectories
import audio.tembra.engine.FakeAudioEngine
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The project folders, the library index and the document round trip over a fake file system (contracts/project-storage.md). */
@OptIn(ExperimentalCoroutinesApi::class)
class OkioProjectRepositoryTest {

    private val clock = FakeClock()
    private val fileSystem = FakeFileSystem(clock)
    private val directories = FakeAppDirectories()
    private val folders = ProjectFolders(directories)
    private val engine = FakeAudioEngine()
    private val demoBytes = mapOf("drums" to ByteArray(300) { 1 }, "bass" to ByteArray(300) { 2 }, "keys" to ByteArray(600) { 3 }, "pad" to ByteArray(600) { 4 })
    private val demoAudio = FakeDemoAudioSource(demoBytes)
    private val codec = ProjectDocumentCodec()

    private val loopBytes = ByteArray(2_000) { (it % 7).toByte() }
    private val loopId = DataFixtures.assetId(loopBytes)

    private fun TestScope.repository(): OkioProjectRepository =
        OkioProjectRepository(directories, engine, demoAudio, fileSystem, StandardTestDispatcher(testScheduler), clock = { clock.nowMs })

    private fun readDocument(path: Path): Project = assertIs<DecodeResult.Decoded>(codec.decode(fileSystem.read(path) { readUtf8() })).document.project

    private fun documentSize(folder: ProjectFolder): Long = fileSystem.metadata(folder.document).size ?: 0L

    /** Puts the loop asset's bytes and a complete overview in place and returns the project holding one clip of it. */
    private fun withLoop(project: Project): Project {
        val folder = folders.folder(project.id)
        fileSystem.createDirectories(folder.audio)
        fileSystem.createDirectories(folder.cacheRoot)
        fileSystem.write(folder.audioPath(loopId, AudioFormat.Wav)) { write(loopBytes) }
        fileSystem.write(folder.overviewPath(loopId)) { write(DataFixtures.peaks(2, 1_536_000) { _, _ -> 64 }) }
        return project.copy(
            assets = persistentMapOf(loopId to DataFixtures.stereoAsset(byteSize = loopBytes.size.toLong())),
            tracks = persistentListOf(DataFixtures.track(1, DataFixtures.clip(1, loopId))),
        )
    }

    @Test
    fun createWritesTheDocumentAndIndexesIt() = runTest {
        val repository = repository()
        val project = repository.create("Song")
        assertEquals("Song", project.name)
        assertEquals(Tempo(120), project.tempo)
        assertTrue(project.tracks.isEmpty())
        val folder = folders.folder(project.id)
        assertTrue(fileSystem.exists(folder.document))
        assertFalse(fileSystem.exists(folder.documentStaging))
        assertEquals(project, readDocument(folder.document))
        val index = repository.library().first()
        val entry = index.entries.single()
        assertEquals(project.id, entry.id)
        assertEquals("Song", entry.name)
        assertEquals(0, entry.trackCount)
        assertEquals(0.0, entry.lengthSeconds)
        assertEquals(ProjectOrigin.User, entry.origin)
        assertEquals(fileSystem.metadata(folder.document).size, entry.byteSize)
        assertEquals(clock.nowMs, entry.lastEdited)
        assertEquals(CardOverview.Empty, entry.overview)
        assertNull(entry.unavailable)
        assertTrue(fileSystem.exists(folders.libraryIndex))
    }

    @Test
    fun saveStagesThenRenamesAndUpdatesTheIndexEntry() = runTest {
        val repository = repository()
        val created = repository.create("Song")
        val edited = withLoop(created).copy(name = "Renamed", description = "Take two")
        clock.nowMs += 10_000
        repository.save(edited)
        val folder = folders.folder(created.id)
        assertFalse(fileSystem.exists(folder.documentStaging))
        assertEquals(edited, readDocument(folder.document))
        val entry = repository.library().first().entries.single()
        assertEquals("Renamed", entry.name)
        assertEquals("Take two", entry.description)
        assertEquals(1, entry.trackCount)
        assertEquals(32.0, entry.lengthSeconds)
        assertEquals(clock.nowMs, entry.lastEdited)
        assertEquals(48, entry.overview.spans)
        assertEquals(documentSize(folder) + loopBytes.size, entry.byteSize)
    }

    @Test
    fun openReturnsTheProjectWithItsPathsAndMissingAssets() = runTest {
        val repository = repository()
        val created = repository.create("Song")
        val missingId = AudioAssetId("e".repeat(64))
        val unknownFormatId = AudioAssetId("f".repeat(64))
        val project = withLoop(created).let {
            it.copy(assets = it.assets.put(missingId, DataFixtures.stereoAsset()).put(unknownFormatId, DataFixtures.stereoAsset().copy(format = null)))
        }
        repository.save(project)
        val opened = assertIs<OpenResult.Opened>(repository.open(created.id)).opened
        assertEquals(project, opened.project)
        val folder = folders.folder(created.id)
        assertEquals(folder.audioPath(loopId, AudioFormat.Wav).toString(), opened.audioPath(loopId))
        assertEquals(folder.overviewPath(loopId).toString(), opened.overviewPath(loopId))
        assertEquals(setOf(missingId, unknownFormatId), opened.missingAssets)
        assertNull(opened.audioPath(unknownFormatId))
        assertEquals(OpenResult.Missing(ProjectId("nope")), repository.open(ProjectId("nope")))
    }

    @Test
    fun anInvalidDocumentOpensAsUnavailableAndIsFlaggedInTheIndex() = runTest {
        val repository = repository()
        val created = repository.create("Song")
        val folder = folders.folder(created.id)
        val broken = codec.encode(ProjectDocument(project = created)).replace("\"tempo\": 120", "\"tempo\": 300")
        clock.nowMs += 1_000
        fileSystem.write(folder.document) { writeUtf8(broken) }
        val result = assertIs<OpenResult.Unavailable>(repository.open(created.id))
        assertEquals(created.id, result.id)
        assertTrue("300" in result.reason, result.reason)
        val entry = repository.library().first().entries.single()
        assertEquals("Song", entry.name)
        assertEquals(result.reason, entry.unavailable)
    }

    @Test
    fun libraryEmitsOnSubscriptionAndAfterEveryChange() = runTest {
        val repository = repository()
        val emissions = mutableListOf<LibraryIndex>()
        backgroundScope.launch { repository.library().toList(emissions) }
        testScheduler.runCurrent()
        assertEquals(1, emissions.size)
        assertTrue(emissions[0].entries.isEmpty())
        val project = repository.create("Song")
        testScheduler.runCurrent()
        assertEquals(listOf("Song"), emissions.last().entries.map { it.name })
        repository.save(project.copy(name = "Changed"))
        testScheduler.runCurrent()
        assertEquals(listOf("Changed"), emissions.last().entries.map { it.name })
        repository.setOpen(project.id)
        testScheduler.runCurrent()
        assertEquals(project.id, emissions.last().openProjectId)
        assertTrue(emissions.size >= 4)
    }

    @Test
    fun aMissingIndexIsRebuiltFromTheFolders() = runTest {
        val repository = repository()
        val first = repository.create("One")
        repository.create("Two")
        repository.setOpen(first.id)
        fileSystem.delete(folders.libraryIndex)
        val index = repository.library().first()
        assertEquals(setOf("One", "Two"), index.entries.map { it.name }.toSet())
        assertNull(index.openProjectId)
        assertTrue(fileSystem.exists(folders.libraryIndex))
    }

    @Test
    fun anUnreadableIndexIsRebuilt() = runTest {
        val repository = repository()
        repository.create("One")
        fileSystem.write(folders.libraryIndex) { writeUtf8("{ not json") }
        assertEquals(listOf("One"), repository.library().first().entries.map { it.name })
    }

    @Test
    fun aDocumentNewerThanTheIndexRebuildsIt() = runTest {
        val repository = repository()
        val created = repository.create("One")
        clock.nowMs += 5_000
        fileSystem.write(folders.folder(created.id).document) { writeUtf8(codec.encode(ProjectDocument(project = created.copy(name = "Edited elsewhere")))) }
        val index = repository.library().first()
        assertEquals(listOf("Edited elsewhere"), index.entries.map { it.name })
        assertEquals(clock.nowMs, index.entries.single().lastEdited)
    }

    @Test
    fun duplicateCopiesTheFolderUnderANewNameAndNewIds() = runTest {
        val repository = repository()
        val created = repository.create("Song")
        val project = withLoop(created)
        repository.save(project)
        repository.saveViewState(created.id, ProjectViewState(zoom = ZoomLevel(12.5), grid = GridSize.Bar))
        val copy = assertIs<Project>(repository.duplicate(created.id))
        assertEquals("Song copy", copy.name)
        assertNotEquals(project.id, copy.id)
        assertNotEquals(project.tracks[0].id, copy.tracks[0].id)
        assertNotEquals(project.tracks[0].clips[0].id, copy.tracks[0].clips[0].id)
        assertEquals(loopId, copy.tracks[0].clips[0].assetId)
        assertEquals(project.assets, copy.assets)
        assertEquals(project.tracks[0].name, copy.tracks[0].name)
        val copyFolder = folders.folder(copy.id)
        assertContentEquals(loopBytes, fileSystem.read(copyFolder.audioPath(loopId, AudioFormat.Wav)) { readByteArray() })
        assertTrue(fileSystem.exists(copyFolder.overviewPath(loopId)))
        assertEquals(ProjectViewState(zoom = ZoomLevel(12.5), grid = GridSize.Bar), repository.viewState(copy.id))
        assertEquals(copy, readDocument(copyFolder.document))
        val second = assertIs<Project>(repository.duplicate(created.id))
        assertEquals("Song copy 2", second.name)
        val third = assertIs<Project>(repository.duplicate(copy.id))
        assertEquals("Song copy copy", third.name)
        val index = repository.library().first()
        assertEquals(4, index.entries.size)
        assertTrue(index.entries.all { it.origin == ProjectOrigin.User })
        assertNull(repository.duplicate(ProjectId("nope")))
    }

    @Test
    fun deleteRemovesBothFoldersAndClearsTheOpenProject() = runTest {
        val repository = repository()
        val created = repository.create("Song")
        val project = withLoop(created)
        repository.save(project)
        repository.setOpen(created.id)
        val folder = folders.folder(created.id)
        assertTrue(fileSystem.exists(folder.cacheRoot))
        repository.delete(created.id)
        assertFalse(fileSystem.exists(folder.root))
        assertFalse(fileSystem.exists(folder.cacheRoot))
        val index = repository.library().first()
        assertTrue(index.entries.isEmpty())
        assertNull(index.openProjectId)
        repository.delete(created.id)
    }

    @Test
    fun installDemoIsIdempotentAndRestoreReplacesIt() = runTest {
        val repository = repository()
        val demo = repository.installDemo()
        assertEquals("Demo project", demo.name)
        assertEquals(Tempo(124), demo.tempo)
        assertEquals(4, demo.tracks.size)
        val folder = folders.folder(demo.id)
        assertEquals(4, fileSystem.list(folder.audio).size)
        assertEquals(demo.id, repository.installDemo().id)
        val index = repository.library().first()
        assertEquals(1, index.entries.size)
        assertEquals(ProjectOrigin.Demo, index.entries.single().origin)
        repository.setOpen(demo.id)
        repository.save(demo.copy(name = "Edited demo"))
        val restored = repository.restoreDemo()
        assertNotEquals(demo.id, restored.id)
        assertEquals("Demo project", restored.name)
        assertFalse(fileSystem.exists(folder.root))
        val after = repository.library().first()
        assertEquals(listOf(restored.id), after.entries.map { it.id })
        assertEquals(ProjectOrigin.Demo, after.entries.single().origin)
        assertEquals(restored.id, after.openProjectId)
        assertEquals(8, engine.calls.count { it.startsWith("buildOverview(") })
    }

    @Test
    fun sizeOnDeviceSumsTheDocumentAndTheAudioButNotTheCache() = runTest {
        val repository = repository()
        val created = repository.create("Song")
        val project = withLoop(created)
        repository.save(project)
        val folder = folders.folder(created.id)
        assertTrue(fileSystem.exists(folder.overviewPath(loopId)))
        assertEquals(documentSize(folder) + loopBytes.size, repository.sizeOnDevice(created.id))
        assertEquals(0L, repository.sizeOnDevice(ProjectId("nope")))
    }

    @Test
    fun unusedAudioIsDeletedOnlyWhenRetired() = runTest {
        val repository = repository()
        val created = repository.create("Song")
        val project = withLoop(created)
        repository.save(project)
        val folder = folders.folder(created.id)
        val withoutClip = project.copy(tracks = persistentListOf(), assets = persistentMapOf())
        repository.save(withoutClip)
        assertTrue(fileSystem.exists(folder.audioPath(loopId, AudioFormat.Wav)))
        assertTrue(fileSystem.exists(folder.overviewPath(loopId)))
        repository.save(withoutClip, retiredAssets = setOf(loopId))
        assertFalse(fileSystem.exists(folder.audioPath(loopId, AudioFormat.Wav)))
        assertFalse(fileSystem.exists(folder.overviewPath(loopId)))
        assertEquals(fileSystem.metadata(folder.document).size, repository.library().first().entries.single().byteSize)
    }

    @Test
    fun viewStateLivesInViewJsonAndNeverTouchesTheDocument() = runTest {
        val repository = repository()
        val created = repository.create("Song")
        val folder = folders.folder(created.id)
        assertEquals(ProjectViewState(), repository.viewState(created.id))
        assertNull(ProjectViewState().zoom)
        assertEquals(GridSize.Sixteenth, ProjectViewState().grid)
        val documentBefore = fileSystem.read(folder.document) { readByteArray() }
        val state = ProjectViewState(zoom = ZoomLevel(24.0), grid = GridSize.Eighth)
        repository.saveViewState(created.id, state)
        assertEquals(state, repository.viewState(created.id))
        assertTrue(fileSystem.exists(folder.view))
        assertFalse(fileSystem.exists(folder.viewStaging))
        assertContentEquals(documentBefore, fileSystem.read(folder.document) { readByteArray() })
        assertEquals(ProjectViewState(), repository.viewState(ProjectId("nope")))
    }

    @Test
    fun importAudioRunsThePipelineInsideTheProjectFolder() = runTest {
        val repository = repository()
        val created = repository.create("Song")
        val bytes = ByteArray(4_096) { (it * 3).toByte() }
        val result = assertIs<ImportResult.Accepted>(repository.importAudio(created.id, PickedFile("take.wav") { Buffer().write(bytes) }))
        assertEquals(DataFixtures.assetId(bytes), result.assetId)
        val folder = folders.folder(created.id)
        assertTrue(fileSystem.exists(folder.audioPath(result.assetId, AudioFormat.Wav)))
        assertTrue(engine.calls.any { it.startsWith("inspect(") })
        assertTrue(engine.calls.contains("buildOverview(${folder.audioPath(result.assetId, AudioFormat.Wav)})"))
    }

    @Test
    fun overviewStatusFollowsThePeaksHeader() = runTest {
        val repository = repository()
        val created = repository.create("Song")
        val folder = folders.folder(created.id)
        val assetId = AudioAssetId("a".repeat(64))
        assertEquals(OverviewStatus.Building(0f), repository.overviewStatus(created.id, assetId).first())
        fileSystem.createDirectories(folder.cacheRoot)
        fileSystem.write(folder.overviewPath(assetId)) { write(DataFixtures.peaks(1, 256 * 100, completedBaseBuckets = 25) { _, _ -> 10 }) }
        assertEquals(OverviewStatus.Building(0.25f), repository.overviewStatus(created.id, assetId).first())
        fileSystem.write(folder.overviewPath(assetId)) { write(DataFixtures.peaks(1, 256 * 100) { _, _ -> 10 }) }
        assertEquals(OverviewStatus.Ready, repository.overviewStatus(created.id, assetId).first())
    }

    @Test
    fun overviewStatusPollsUntilReadyAndReportsAFailedBuild() = runTest {
        val repository = repository()
        val created = repository.create("Song")
        val folder = folders.folder(created.id)
        val assetId = AudioAssetId("b".repeat(64))
        backgroundScope.launch {
            delay(1_200)
            fileSystem.createDirectories(folder.cacheRoot)
            fileSystem.write(folder.overviewPath(assetId)) { write(DataFixtures.peaks(1, 512) { _, _ -> 10 }) }
        }
        val statuses = repository.overviewStatus(created.id, assetId).toList()
        assertEquals(listOf(OverviewStatus.Building(0f), OverviewStatus.Ready), statuses)
        assertTrue(testScheduler.currentTime >= 1_200, "polled until the file appeared at ${testScheduler.currentTime} ms")
        val project = withLoop(created)
        repository.save(project)
        fileSystem.delete(folder.overviewPath(loopId))
        engine.overviewWriter = { false }
        assertFalse(repository.rebuildOverview(created.id, loopId))
        assertEquals(OverviewStatus.Failed, repository.overviewStatus(created.id, loopId).first())
        engine.overviewWriter = { path -> fileSystem.write(path.toPath()) { write(DataFixtures.peaks(2, 1_536_000) { _, _ -> 3 }) }; true }
        assertTrue(repository.rebuildOverview(created.id, loopId))
        assertEquals(OverviewStatus.Ready, repository.overviewStatus(created.id, loopId).first())
    }
}
