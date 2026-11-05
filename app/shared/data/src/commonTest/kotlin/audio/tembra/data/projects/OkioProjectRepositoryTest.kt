package audio.tembra.data.projects

import audio.tembra.core.metronome.Tempo
import audio.tembra.core.project.AudioAssetId
import audio.tembra.core.project.AudioFormat
import audio.tembra.core.project.Beats
import audio.tembra.core.project.GridSize
import audio.tembra.core.project.Project
import audio.tembra.core.project.ProjectDocument
import audio.tembra.core.project.ProjectId
import audio.tembra.core.project.TrackId
import audio.tembra.core.project.ZoomLevel
import audio.tembra.core.recording.TakeStopReason
import audio.tembra.data.metronome.FakeAppDirectories
import audio.tembra.engine.AudioInfo
import audio.tembra.engine.FakeAudioEngine
import audio.tembra.engine.TakeInfo
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

/**
 * The project folders, the library index and the document round trip over a fake file system (contracts/project-storage.md),
 * and the staged takes of contracts/take-storage.md: staging, listing, discarding, adoption and crash recovery.
 */
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

    private fun takeManifest(token: String, startedAt: Long = clock.nowMs, takeName: String = "Take 1", startBeats: Double = 0.0): TakeManifest =
        TakeManifest(token, TrackId("track-1"), Beats(startBeats), takeName, startedAt, alignmentFrames = 0, sampleRate = 48_000, channels = 1)

    /** A staged take as the controller and the recorder leave it: the manifest and, unless [withWav] is false, the file of [bytes]. */
    private fun stageTake(folder: ProjectFolder, token: String, startedAt: Long = clock.nowMs, withWav: Boolean = true, bytes: ByteArray = ByteArray(1_000) { 7 }): TakeManifest {
        val manifest = takeManifest(token, startedAt)
        fileSystem.createDirectories(folder.audio)
        fileSystem.write(folder.takeManifestPath(token)) { writeUtf8(manifest.toJson()) }
        if (withWav) fileSystem.write(folder.takeStagingPath(token)) { write(bytes) }
        return manifest
    }

    private fun takeInfo(frames: Long, channels: Int = 1, sampleRate: Int = 48_000, startBeats: Double = 0.0): TakeInfo =
        TakeInfo(startFrames = 0, startBeats = startBeats, frames = frames, channels = channels, sampleRate = sampleRate, alignmentFrames = 0, stopReason = TakeStopReason.User)

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
            it.copy(assets = it.assets.putting(missingId, DataFixtures.stereoAsset()).putting(unknownFormatId, DataFixtures.stereoAsset().copy(format = null)))
        }
        repository.save(project)
        val result = repository.open(created.id)
        val opened = assertIs<OpenResult.Opened>(result, "$result").opened
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

    // --- Staged takes (feature 010, contracts/take-storage.md) ---------------------------------------------------------

    @Test
    fun pendingTakesListsOnlyManifestsWithAReadableWavAndDropsTheRest() = runTest {
        val repository = repository()
        val created = repository.create("Song")
        val folder = folders.folder(created.id)
        val later = stageTake(folder, "later", startedAt = 2_000)
        val earlier = stageTake(folder, "earlier", startedAt = 1_000)
        stageTake(folder, "no-file", withWav = false)
        stageTake(folder, "unreadable")
        engine.inspectResults[folder.takeStagingPath("unreadable").toString()] = null
        fileSystem.write(folder.takeManifestPath("broken")) { writeUtf8("{ not json") }
        fileSystem.write(folder.takeStagingPath("broken")) { write(ByteArray(10)) }
        fileSystem.write(folder.takeManifestPath("renamed")) { writeUtf8(takeManifest("other-token").toJson()) }
        fileSystem.write(folder.takeStagingPath("renamed")) { write(ByteArray(10)) }
        assertEquals(listOf(earlier, later), repository.pendingTakes(created.id), "readable takes, oldest first")
        for (token in listOf("no-file", "unreadable", "broken", "renamed")) {
            assertFalse(fileSystem.exists(folder.takeManifestPath(token)), "the $token manifest is dropped")
            assertFalse(fileSystem.exists(folder.takeStagingPath(token)), "the $token file is dropped")
        }
        assertTrue(fileSystem.exists(folder.takeStagingPath("earlier")))
        assertTrue(fileSystem.exists(folder.takeManifestPath("later")))
        assertEquals(listOf(earlier, later), repository.pendingTakes(created.id), "listing is repeatable")
        assertTrue(repository.pendingTakes(ProjectId("nope")).isEmpty())
    }

    @Test
    fun discardTakeDeletesBothStagingFiles() = runTest {
        val repository = repository()
        val created = repository.create("Song")
        val folder = folders.folder(created.id)
        stageTake(folder, "t1")
        stageTake(folder, "t2")
        repository.discardTake(created.id, "t1")
        assertFalse(fileSystem.exists(folder.takeManifestPath("t1")))
        assertFalse(fileSystem.exists(folder.takeStagingPath("t1")))
        assertTrue(fileSystem.exists(folder.takeManifestPath("t2")))
        assertTrue(fileSystem.exists(folder.takeStagingPath("t2")))
        assertEquals(listOf("t2"), repository.pendingTakes(created.id).map { it.token })
        repository.discardTake(created.id, "t1")
        repository.discardTake(ProjectId("nope"), "t1")
    }

    @Test
    fun stagedTakesAreInvisibleToTheSizeAndTheRetireSweep() = runTest {
        val repository = repository()
        val created = repository.create("Song")
        val project = withLoop(created)
        val folder = folders.folder(created.id)
        stageTake(folder, "t1", bytes = ByteArray(5_000) { 9 })
        repository.save(project)
        assertEquals(documentSize(folder) + loopBytes.size, repository.sizeOnDevice(created.id))
        assertEquals(documentSize(folder) + loopBytes.size, repository.library().first().entries.single().byteSize)
        val withoutClip = project.copy(tracks = persistentListOf(), assets = persistentMapOf())
        repository.save(withoutClip, retiredAssets = setOf(loopId))
        assertFalse(fileSystem.exists(folder.audioPath(loopId, AudioFormat.Wav)), "the retired copy is gone")
        assertTrue(fileSystem.exists(folder.takeStagingPath("t1")), "the staged take survives the sweep")
        assertTrue(fileSystem.exists(folder.takeManifestPath("t1")))
        assertEquals(documentSize(folder), repository.sizeOnDevice(created.id))
        assertEquals(listOf("t1"), repository.pendingTakes(created.id).map { it.token })
    }

    @Test
    fun stageTakeWritesTheManifestAndNamesTheStagingFile() = runTest {
        val repository = repository()
        val created = repository.create("Song")
        val folder = folders.folder(created.id)
        val manifest = takeManifest("t1", takeName = "Take 3", startBeats = 8.0)
        assertFalse(fileSystem.exists(folder.audio), "a new project has no audio folder yet")
        val path = repository.stageTake(created.id, manifest)
        assertEquals(folder.takeStagingPath("t1").toString(), path)
        assertEquals(folder.audio.toString(), repository.audioDirectory(created.id))
        assertEquals(manifest, TakeManifest.parse(fileSystem.read(folder.takeManifestPath("t1")) { readUtf8() }))
        assertFalse(fileSystem.exists(folder.takeStagingPath("t1")), "the engine creates the file, not the repository")
        assertEquals(documentSize(folder), repository.sizeOnDevice(created.id))
    }

    @Test
    fun adoptTakeRunsTheAdopterInsideTheProjectFolderAndTracksItsOverview() = runTest {
        val repository = repository()
        val created = repository.create("Song")
        val folder = folders.folder(created.id)
        val manifest = takeManifest("t1", takeName = "Take 2", startBeats = 4.0)
        val bytes = ByteArray(4_000) { (it * 5).toByte() }
        val path = repository.stageTake(created.id, manifest)
        fileSystem.write(path.toPath()) { write(bytes) }
        engine.inspectResults[path] = AudioInfo(AudioFormat.Wav, 1, 48_000, 1_000)
        engine.overviewWriter = { overview -> fileSystem.write(overview.toPath()) { write(DataFixtures.peaks(1, 1_000) { _, _ -> 3 }) }; true }
        val adopted = assertIs<AdoptedTake.Adopted>(repository.adoptTake(created.id, manifest, takeInfo(frames = 1_000, startBeats = 4.0)))
        assertEquals(DataFixtures.assetId(bytes), adopted.assetId)
        assertEquals("Take 2", adopted.clip.name)
        assertEquals(Beats(4.0), adopted.startBeats)
        assertEquals(folder.audioPath(adopted.assetId, AudioFormat.Wav).toString(), adopted.audioPath)
        assertTrue(fileSystem.exists(folder.audioPath(adopted.assetId, AudioFormat.Wav)))
        assertFalse(fileSystem.exists(folder.takeManifestPath("t1")))
        assertEquals(OverviewStatus.Ready, repository.overviewStatus(created.id, adopted.assetId).first())
        assertTrue(repository.pendingTakes(created.id).isEmpty())

        val second = takeManifest("t2")
        val secondBytes = ByteArray(4_000) { (it * 11).toByte() }
        val secondPath = repository.stageTake(created.id, second)
        fileSystem.write(secondPath.toPath()) { write(secondBytes) }
        engine.inspectResults[secondPath] = AudioInfo(AudioFormat.Wav, 1, 48_000, 1_000)
        engine.overviewWriter = { false }
        val failedOverview = assertIs<AdoptedTake.Adopted>(repository.adoptTake(created.id, second, takeInfo(frames = 1_000)))
        assertEquals(OverviewStatus.Failed, repository.overviewStatus(created.id, failedOverview.assetId).first(), "a failed build is reported, the clip exists")
        assertEquals(AdoptedTake.Discarded, repository.adoptTake(created.id, takeManifest("t3"), takeInfo(frames = 0)))
    }

    @Test
    fun adoptPendingTakeRecoversAReadableStagedTakeAndDiscardsTheRest() = runTest {
        val repository = repository()
        val created = repository.create("Song")
        val folder = folders.folder(created.id)
        val bytes = ByteArray(3_000) { (it * 3).toByte() }
        val recovered = stageTake(folder, "t1", bytes = bytes).copy(startBeats = Beats(16.0), alignmentFrames = 120, takeName = "Take 5")
        fileSystem.write(folder.takeManifestPath("t1")) { writeUtf8(recovered.toJson()) }
        engine.inspectResults[folder.takeStagingPath("t1").toString()] = AudioInfo(AudioFormat.Wav, 2, 44_100, 44_100)
        val adopted = assertIs<AdoptedTake.Adopted>(repository.adoptPendingTake(created.id, recovered))
        assertEquals(DataFixtures.assetId(bytes), adopted.assetId)
        assertEquals(44_100L, adopted.clip.lengthFrames)
        assertEquals("Take 5", adopted.clip.name)
        assertEquals(Beats(16.0), adopted.startBeats)
        assertEquals(2, adopted.asset.channels)
        assertEquals(44_100, adopted.asset.sampleRate)
        assertFalse(fileSystem.exists(folder.takeStagingPath("t1")))
        assertFalse(fileSystem.exists(folder.takeManifestPath("t1")))

        val unreadable = stageTake(folder, "t2")
        engine.inspectResults[folder.takeStagingPath("t2").toString()] = null
        assertEquals(AdoptedTake.Discarded, repository.adoptPendingTake(created.id, unreadable))
        assertFalse(fileSystem.exists(folder.takeStagingPath("t2")))
        assertFalse(fileSystem.exists(folder.takeManifestPath("t2")))

        val tooShort = stageTake(folder, "t3")
        engine.inspectResults[folder.takeStagingPath("t3").toString()] = AudioInfo(AudioFormat.Wav, 1, 48_000, 479)
        assertEquals(AdoptedTake.Discarded, repository.adoptPendingTake(created.id, tooShort))
        assertFalse(fileSystem.exists(folder.takeStagingPath("t3")), "shorter than 0.01 s is discarded with its file")
        assertFalse(fileSystem.exists(folder.takeManifestPath("t3")))
        assertEquals(listOf(folder.audioPath(adopted.assetId, AudioFormat.Wav)), fileSystem.list(folder.audio))
    }
}
