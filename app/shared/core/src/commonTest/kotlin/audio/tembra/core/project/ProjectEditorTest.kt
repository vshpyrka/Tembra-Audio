package audio.tembra.core.project

import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.core.project.ProjectFixtures.clip
import audio.tembra.core.project.ProjectFixtures.clipId
import audio.tembra.core.project.ProjectFixtures.monoAsset
import audio.tembra.core.project.ProjectFixtures.monoAssetId
import audio.tembra.core.project.ProjectFixtures.project
import audio.tembra.core.project.ProjectFixtures.stereoAssetId
import audio.tembra.core.project.ProjectFixtures.track
import audio.tembra.core.project.ProjectFixtures.trackId
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Every `ProjectEdit` and every rejection of data-model.md section 1 (spec FR-019 to FR-027, FR-031 to FR-037, FR-044, FR-052). */
class ProjectEditorTest {

    private fun applied(result: EditResult): Project = assertIs<EditResult.Applied>(result).project

    private fun rejected(result: EditResult): EditRejection = assertIs<EditResult.Rejected>(result).reason

    private fun assertClose(expected: Double, actual: Double) = assertTrue(abs(expected - actual) < 1e-9, "expected $expected, was $actual")

    // --- Tracks ------------------------------------------------------------------------------------------------------

    @Test
    fun addTrackAppendsTrackNWithTheNextColourAndDefaults() {
        val first = applied(ProjectEditor.apply(ProjectFixtures.empty, AddTrack(TrackId("a"))))
        assertEquals(listOf("Track 1"), first.tracks.map { it.name })
        val added = first.tracks.single()
        assertEquals(TrackId("a"), added.id)
        assertEquals(0, added.colorIndex)
        assertNull(added.label)
        assertEquals(GainDb.Unity, added.gainDb)
        assertFalse(added.muted)
        assertFalse(added.soloed)
        val second = applied(ProjectEditor.apply(first, AddTrack(TrackId("b"))))
        assertEquals(listOf("Track 1", "Track 2"), second.tracks.map { it.name })
        assertEquals(1, second.tracks[1].colorIndex)
    }

    @Test
    fun addTrackNumbersPastTheHighestNameAndCyclesSixColours() {
        var project = ProjectFixtures.empty
        repeat(6) { project = applied(ProjectEditor.apply(project, AddTrack(TrackId("t$it")))) }
        assertEquals(listOf(0, 1, 2, 3, 4, 5), project.tracks.map { it.colorIndex })
        val seventh = applied(ProjectEditor.apply(project, AddTrack(TrackId("t6"))))
        assertEquals(0, seventh.tracks.last().colorIndex)
        assertEquals("Track 7", seventh.tracks.last().name)
        val withoutSecond = applied(ProjectEditor.apply(seventh, DeleteTrack(TrackId("t1"))))
        val again = applied(ProjectEditor.apply(withoutSecond, AddTrack(TrackId("t7"))))
        assertEquals("Track 8", again.tracks.last().name)
        assertEquals(1, again.tracks.last().colorIndex)
    }

    @Test
    fun addTrackIsRefusedAtTheEngineBound() {
        var project = ProjectFixtures.empty
        repeat(64) { project = applied(ProjectEditor.apply(project, AddTrack(TrackId("t$it")))) }
        assertEquals(EditRejection.TrackLimitReached, rejected(ProjectEditor.apply(project, AddTrack(TrackId("t64")))))
    }

    @Test
    fun renameTrackTrimsRevertsEmptyToTrackNAndRefusesLongNames() {
        val project = project(track(1), track(2, name = "Lead"))
        assertEquals("Vox", applied(ProjectEditor.apply(project, RenameTrack(trackId(2), "  Vox "))).tracks[1].name)
        assertEquals("Track 2", applied(ProjectEditor.apply(project, RenameTrack(trackId(2), "   "))).tracks[1].name)
        assertEquals(EditRejection.TrackNameTooLong, rejected(ProjectEditor.apply(project, RenameTrack(trackId(2), "x".repeat(41)))))
        assertEquals(EditRejection.UnknownTrack(TrackId("nope")), rejected(ProjectEditor.apply(project, RenameTrack(TrackId("nope"), "x"))))
    }

    @Test
    fun labelGainMuteAndSolo() {
        val project = project(track(1), track(2))
        assertEquals(InstrumentLabel.Drums, applied(ProjectEditor.apply(project, SetLabel(trackId(1), InstrumentLabel.Drums))).tracks[0].label)
        assertNull(applied(ProjectEditor.apply(project, SetLabel(trackId(1), null))).tracks[0].label)
        assertEquals(GainDb(-2.4f), applied(ProjectEditor.apply(project, SetGain(trackId(2), GainDb(-2.4f)))).tracks[1].gainDb)
        assertTrue(applied(ProjectEditor.apply(project, SetMuted(trackId(2), true))).tracks[1].muted)
        assertTrue(applied(ProjectEditor.apply(project, SetSoloed(trackId(1), true))).tracks[0].soloed)
        assertEquals(EditRejection.UnknownTrack(TrackId("x")), rejected(ProjectEditor.apply(project, SetGain(TrackId("x"), GainDb.Unity))))
    }

    @Test
    fun moveTrackSwapsWithItsNeighbourAndIsRefusedAtTheEnds() {
        val project = project(track(1), track(2), track(3))
        assertEquals(listOf(2, 1, 3), applied(ProjectEditor.apply(project, MoveTrack(trackId(2), MoveDirection.Up))).tracks.map { it.id.value.removePrefix("track-").toInt() })
        assertEquals(listOf(1, 3, 2), applied(ProjectEditor.apply(project, MoveTrack(trackId(2), MoveDirection.Down))).tracks.map { it.id.value.removePrefix("track-").toInt() })
        assertEquals(EditRejection.TrackAtEnd, rejected(ProjectEditor.apply(project, MoveTrack(trackId(1), MoveDirection.Up))))
        assertEquals(EditRejection.TrackAtEnd, rejected(ProjectEditor.apply(project, MoveTrack(trackId(3), MoveDirection.Down))))
    }

    @Test
    fun deleteTrackRemovesOnlyThatTrackClearsItsSoloAndPrunesUnusedAssets() {
        val project = project(
            track(1, clip(1, 0.0), muted = true),
            track(2, clip(2, 0.0, assetId = monoAssetId, length = 441_000), soloed = true),
        )
        assertEquals(setOf(trackId(2)), project.audibleTracks)
        val after = applied(ProjectEditor.apply(project, DeleteTrack(trackId(2))))
        assertEquals(listOf(trackId(1)), after.tracks.map { it.id })
        assertTrue(after.tracks[0].muted)
        assertEquals(emptySet(), after.audibleTracks)
        assertEquals(setOf(stereoAssetId), after.assets.keys)
        assertEquals(EditRejection.UnknownTrack(trackId(9)), rejected(ProjectEditor.apply(project, DeleteTrack(trackId(9)))))
    }

    // --- Import ------------------------------------------------------------------------------------------------------

    @Test
    fun importToTrackPlacesTheClipAtThePlayheadOnAFreeTrack() {
        val project = project(track(1), assets = emptyMap())
        val clip = clip(1, 8.0)
        val after = applied(ProjectEditor.apply(project, ImportToTrack(trackId(1), listOf(clip), mapOf(stereoAssetId to ProjectFixtures.stereoAsset))))
        assertEquals(listOf(clip), after.tracks[0].clips)
        assertEquals(ProjectFixtures.stereoAsset, after.assets[stereoAssetId])
    }

    @Test
    fun importToTrackMovesToTheFirstFreePositionWhenThePlayheadIsInsideAClip() {
        val project = project(track(1, clip(1, 0.0), clip(2, 70.0)))
        val imported = clip(3, 10.0, assetId = monoAssetId, length = 441_000)
        val after = applied(ProjectEditor.apply(project, ImportToTrack(trackId(1), listOf(imported))))
        val placed = after.tracks[0].clips.single { it.id == clipId(3) }
        assertClose(134.0, placed.startBeats.value)
        assertEquals(listOf(clipId(1), clipId(2), clipId(3)), after.tracks[0].clips.map { it.id })
    }

    @Test
    fun importToTrackUsesAGapThatFitsAndPlacesSeveralClipsOneAfterAnother() {
        val project = project(track(1, clip(1, 0.0), clip(2, 100.0)))
        val a = clip(3, 10.0, assetId = monoAssetId, length = 441_000)
        val b = clip(4, 10.0, assetId = monoAssetId, length = 441_000)
        val after = applied(ProjectEditor.apply(project, ImportToTrack(trackId(1), listOf(a, b))))
        val starts = after.tracks[0].clips.associate { it.id to it.startBeats.value }
        assertClose(64.0, starts.getValue(clipId(3)))
        assertClose(164.0, starts.getValue(clipId(4)))
    }

    @Test
    fun importIsRefusedForAnAssetNeitherTheProjectNorTheEditKnows() {
        val project = project(track(1), assets = emptyMap())
        assertEquals(EditRejection.UnknownAsset(stereoAssetId), rejected(ProjectEditor.apply(project, ImportToTrack(trackId(1), listOf(clip(1, 0.0))))))
        assertEquals(EditRejection.UnknownTrack(trackId(5)), rejected(ProjectEditor.apply(project, ImportToTrack(trackId(5), listOf(clip(1, 0.0)), mapOf(stereoAssetId to ProjectFixtures.stereoAsset)))))
    }

    @Test
    fun importAsNewTracksCreatesOneTrackPerFileInOrderAsOneEdit() {
        val project = project(track(1), assets = emptyMap())
        val edit = ImportAsNewTracks(
            tracks = listOf(
                NewTrackWithClip(TrackId("n1"), "drums", clip(1, 4.0)),
                NewTrackWithClip(TrackId("n2"), "bass take with a very long file name that exceeds forty", clip(2, 4.0, assetId = monoAssetId, length = 441_000)),
            ),
            assets = mapOf(stereoAssetId to ProjectFixtures.stereoAsset, monoAssetId to monoAsset),
        )
        val after = applied(ProjectEditor.apply(project, edit))
        assertEquals(listOf(trackId(1), TrackId("n1"), TrackId("n2")), after.tracks.map { it.id })
        assertEquals("drums", after.tracks[1].name)
        assertEquals("bass take with a very long file name tha", after.tracks[2].name)
        assertEquals(listOf(0, 1, 2), after.tracks.map { it.colorIndex })
        assertEquals(Beats(4.0), after.tracks[1].clips.single().startBeats)
        assertEquals(Beats(4.0), after.tracks[2].clips.single().startBeats)
        assertEquals(setOf(stereoAssetId, monoAssetId), after.assets.keys)
        assertNull(after.tracks[1].label)
    }

    @Test
    fun importIsRefusedPastTheClipBound() {
        val tracks = (1..32).map { n -> track(n, *(1..32).map { c -> clip(n * 100 + c, (c - 1) * 70.0) }.toTypedArray()) }
        val project = project(*tracks.toTypedArray())
        assertEquals(1024, project.tracks.sumOf { it.clips.size })
        assertEquals(EditRejection.ClipLimitReached, rejected(ProjectEditor.apply(project, ImportToTrack(trackId(1), listOf(clip(9999, 5000.0))))))
    }

    // --- Clips -------------------------------------------------------------------------------------------------------

    @Test
    fun splitClipMakesTwoHalvesThatTogetherAreTheOriginal() {
        val project = project(track(1, clip(1, 8.0, name = "loop")))
        val after = applied(ProjectEditor.apply(project, SplitClip(clipId(1), Beats(16.0), leftId = ClipId("L"), rightId = ClipId("R"))))
        val (left, right) = after.tracks[0].clips
        assertEquals(ClipId("L"), left.id)
        assertEquals(ClipId("R"), right.id)
        assertEquals(Beats(8.0), left.startBeats)
        assertEquals(0L, left.offsetFrames)
        assertEquals(192_000L, left.lengthFrames)
        assertClose(16.0, right.startBeats.value)
        assertEquals(192_000L, right.offsetFrames)
        assertEquals(1_344_000L, right.lengthFrames)
        assertEquals("loop", left.name)
        assertEquals("loop", right.name)
        assertNull(after.findClip(clipId(1)))
    }

    @Test
    fun splitIsOnlyAllowedStrictlyInsideAndAwayFromTheEdges() {
        val project = project(track(1, clip(1, 8.0)))
        assertEquals(EditRejection.SplitOutsideClip, rejected(ProjectEditor.apply(project, SplitClip(clipId(1), Beats(8.0)))))
        assertEquals(EditRejection.SplitOutsideClip, rejected(ProjectEditor.apply(project, SplitClip(clipId(1), Beats(72.0)))))
        assertEquals(EditRejection.SplitOutsideClip, rejected(ProjectEditor.apply(project, SplitClip(clipId(1), Beats(4.0)))))
        assertEquals(EditRejection.SplitTooCloseToEdge, rejected(ProjectEditor.apply(project, SplitClip(clipId(1), Beats(8.01)))))
        assertEquals(EditRejection.SplitTooCloseToEdge, rejected(ProjectEditor.apply(project, SplitClip(clipId(1), Beats(71.99)))))
        assertIs<EditResult.Applied>(ProjectEditor.apply(project, SplitClip(clipId(1), Beats(8.03))))
        assertEquals(EditRejection.UnknownClip(clipId(7)), rejected(ProjectEditor.apply(project, SplitClip(clipId(7), Beats(10.0)))))
    }

    @Test
    fun moveClipOnItsTrackNeverGoesBeforeZeroAndStopsAgainstNeighbours() {
        val project = project(track(1, clip(1, 0.0), clip(2, 100.0)))
        assertEquals(Beats.Zero, applied(ProjectEditor.apply(project, MoveClip(clipId(1), trackId(1), Beats(-5.0)))).findClip(clipId(1))!!.startBeats)
        assertClose(64.0, applied(ProjectEditor.apply(project, MoveClip(clipId(2), trackId(1), Beats(50.0)))).findClip(clipId(2))!!.startBeats.value)
        assertClose(36.0, applied(ProjectEditor.apply(project, MoveClip(clipId(1), trackId(1), Beats(80.0)))).findClip(clipId(1))!!.startBeats.value)
        assertClose(300.0, applied(ProjectEditor.apply(project, MoveClip(clipId(2), trackId(1), Beats(300.0)))).findClip(clipId(2))!!.startBeats.value)
        val moved = applied(ProjectEditor.apply(project, MoveClip(clipId(2), trackId(1), Beats(300.0))))
        assertEquals(listOf(clipId(1), clipId(2)), moved.tracks[0].clips.map { it.id })
    }

    @Test
    fun moveClipAcrossTracksNeedsAFreeSpan() {
        val project = project(track(1, clip(1, 0.0)), track(2, clip(2, 30.0)))
        assertEquals(EditRejection.SpanOccupied, rejected(ProjectEditor.apply(project, MoveClip(clipId(1), trackId(2), Beats(0.0)))))
        val after = applied(ProjectEditor.apply(project, MoveClip(clipId(1), trackId(2), Beats(94.0))))
        assertTrue(after.tracks[0].clips.isEmpty())
        assertEquals(listOf(clipId(2), clipId(1)), after.tracks[1].clips.map { it.id })
        assertEquals(Beats(94.0), after.findClip(clipId(1))!!.startBeats)
        assertEquals(EditRejection.UnknownTrack(trackId(3)), rejected(ProjectEditor.apply(project, MoveClip(clipId(1), trackId(3), Beats(0.0)))))
    }

    @Test
    fun deleteClipKeepsAnAssetOtherClipsUseAndPrunesOneNobodyUses() {
        val project = project(track(1, clip(1, 0.0), clip(2, 100.0)), track(2, clip(3, 0.0, assetId = monoAssetId, length = 441_000)))
        val one = applied(ProjectEditor.apply(project, DeleteClip(clipId(1))))
        assertEquals(listOf(clipId(2)), one.tracks[0].clips.map { it.id })
        assertEquals(setOf(stereoAssetId, monoAssetId), one.assets.keys)
        val none = applied(ProjectEditor.apply(one, DeleteClip(clipId(2))))
        assertEquals(setOf(monoAssetId), none.assets.keys)
        assertEquals(EditRejection.UnknownClip(clipId(8)), rejected(ProjectEditor.apply(project, DeleteClip(clipId(8)))))
    }

    // --- Project settings --------------------------------------------------------------------------------------------

    @Test
    fun tempoAndSignatureChangesLeaveBeatsUnchanged() {
        val project = project(track(1, clip(1, 8.0)))
        val faster = applied(ProjectEditor.apply(project, SetTempo(Tempo(240))))
        assertEquals(Tempo(240), faster.tempo)
        assertEquals(Beats(8.0), faster.findClip(clipId(1))!!.startBeats)
        assertEquals(1_536_000L, faster.findClip(clipId(1))!!.lengthFrames)
        val sixEight = applied(ProjectEditor.apply(project, SetTimeSignature(TimeSignature.SixEight)))
        assertEquals(TimeSignature.SixEight, sixEight.timeSignature)
        assertEquals(Beats(8.0), sixEight.findClip(clipId(1))!!.startBeats)
    }

    @Test
    fun masterVolumeNameDescriptionAndKey() {
        val project = project()
        assertEquals(Percent(35), applied(ProjectEditor.apply(project, SetMasterVolume(Percent(35)))).masterVolume)
        assertEquals("My song", applied(ProjectEditor.apply(project, RenameProject("  My song "))).name)
        assertEquals(EditRejection.ProjectNameOutOfRange, rejected(ProjectEditor.apply(project, RenameProject("  "))))
        assertEquals(EditRejection.ProjectNameOutOfRange, rejected(ProjectEditor.apply(project, RenameProject("x".repeat(61)))))
        assertEquals("Four loops", applied(ProjectEditor.apply(project, SetDescription(" Four loops "))).description)
        assertEquals("", applied(ProjectEditor.apply(project, SetDescription(""))).description)
        assertEquals(EditRejection.DescriptionTooLong, rejected(ProjectEditor.apply(project, SetDescription("x".repeat(121)))))
        assertEquals(MusicalKey.DMinor, applied(ProjectEditor.apply(project, SetKey(MusicalKey.DMinor))).key)
        assertNull(applied(ProjectEditor.apply(project.copy(key = MusicalKey.AMajor), SetKey(null))).key)
    }
}
