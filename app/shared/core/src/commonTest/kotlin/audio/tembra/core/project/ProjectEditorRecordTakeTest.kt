package audio.tembra.core.project

import audio.tembra.core.project.ProjectFixtures.clip
import audio.tembra.core.project.ProjectFixtures.clipId
import audio.tembra.core.project.ProjectFixtures.monoAssetId
import audio.tembra.core.project.ProjectFixtures.project
import audio.tembra.core.project.ProjectFixtures.stereoAssetId
import audio.tembra.core.project.ProjectFixtures.track
import audio.tembra.core.project.ProjectFixtures.trackId
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `RecordTake` and `SetCountIn` (feature 010, data-model.md section 1; spec FR-011 to FR-013): the take's clip is
 * named and placed, its asset merged once, every clip on the track overlapping the take's span reduced to its parts
 * outside the span by the split arithmetic, the track left without overlap, the whole change one history snapshot.
 * The fixtures run at 120 BPM in 4/4 with the 48 kHz stereo loop (64 beats, 24 000 frames per beat).
 */
class ProjectEditorRecordTakeTest {

    private val takeAssetId = AudioAssetId("c".repeat(64))
    private val takeClipId = ClipId("take")

    private fun takeAsset(frames: Long): AudioAsset =
        AudioAsset(fileName = "Take 1.wav", format = AudioFormat.Wav, channels = 1, sampleRate = 48000, frames = frames, byteSize = frames * 3 + 44)

    private fun recordTake(trackId: TrackId, start: Double, frames: Long, name: String = "Take 1", id: ClipId = takeClipId, assetId: AudioAssetId = takeAssetId): RecordTake {
        val asset = takeAsset(frames)
        return RecordTake(
            trackId = trackId,
            assetId = assetId,
            asset = asset,
            clipId = id,
            clip = ClipTemplate(assetId = assetId, name = name, offsetFrames = 0, lengthFrames = frames),
            startBeats = Beats(start),
        )
    }

    private fun applied(result: EditResult): Project = assertIs<EditResult.Applied>(result).project

    private fun rejected(result: EditResult): EditRejection = assertIs<EditResult.Rejected>(result).reason

    private fun assertClose(expected: Double, actual: Double) = assertTrue(abs(expected - actual) < 1e-9, "expected $expected, was $actual")

    /** No two clips on the track overlap in beats at the current tempo (spec 009 FR-027), and they are in start order. */
    private fun assertNoOverlap(project: Project, trackId: TrackId) {
        val clips = project.findTrack(trackId)!!.clips
        assertEquals(clips.sortedBy { it.startBeats.value }, clips.toList(), "clips are not in start order")
        clips.zipWithNext { a, b ->
            assertTrue(project.clipEndBeats(a).value <= b.startBeats.value + 1e-9, "${a.id} ends at ${project.clipEndBeats(a).value} after ${b.id} starts at ${b.startBeats.value}")
        }
    }

    // --- The take's clip and asset -----------------------------------------------------------------------------------

    @Test
    fun aTakeOnAnEmptyTrackIsNamedPlacedAndWholeWithItsAssetMerged() {
        val project = project(track(1), assets = emptyMap())
        val after = applied(ProjectEditor.apply(project, recordTake(trackId(1), start = 8.0, frames = 240_000, name = "Take 3")))
        val take = after.tracks[0].clips.single()
        assertEquals(takeClipId, take.id)
        assertEquals(takeAssetId, take.assetId)
        assertEquals(Beats(8.0), take.startBeats)
        assertEquals(0L, take.offsetFrames)
        assertEquals(240_000L, take.lengthFrames)
        assertEquals("Take 3", take.name)
        assertEquals(mapOf(takeAssetId to takeAsset(240_000)), after.assets)
        assertClose(18.0, after.clipEndBeats(take).value)
    }

    @Test
    fun theSameAssetIsMergedOnceAcrossTakes() {
        val project = project(track(1), assets = emptyMap())
        val one = applied(ProjectEditor.apply(project, recordTake(trackId(1), start = 0.0, frames = 48_000, id = ClipId("t1"))))
        val two = applied(ProjectEditor.apply(one, recordTake(trackId(1), start = 100.0, frames = 48_000, id = ClipId("t2"), name = "Take 2")))
        assertEquals(setOf(takeAssetId), two.assets.keys)
        assertEquals(listOf(ClipId("t1"), ClipId("t2")), two.tracks[0].clips.map { it.id })
        assertEquals(listOf("Take 1", "Take 2"), two.tracks[0].clips.map { it.name })
    }

    @Test
    fun theTakeIsInsertedInStartOrderAmongUntouchedClips() {
        val project = project(track(1, clip(1, 0.0), clip(2, 200.0)))
        val after = applied(ProjectEditor.apply(project, recordTake(trackId(1), start = 100.0, frames = 240_000)))
        assertEquals(listOf(clipId(1), takeClipId, clipId(2)), after.tracks[0].clips.map { it.id })
        assertEquals(project.findClip(clipId(1)), after.findClip(clipId(1)))
        assertEquals(project.findClip(clipId(2)), after.findClip(clipId(2)))
        assertNoOverlap(after, trackId(1))
    }

    @Test
    fun otherTracksAreUntouched() {
        val project = project(track(1, clip(1, 0.0)), track(2, clip(2, 0.0)))
        val after = applied(ProjectEditor.apply(project, recordTake(trackId(2), start = 0.0, frames = 1_536_000)))
        assertEquals(project.tracks[0], after.tracks[0])
        assertEquals(listOf(takeClipId), after.tracks[1].clips.map { it.id })
    }

    // --- The trim matrix of FR-013 -----------------------------------------------------------------------------------

    @Test
    fun aClipStraddlingTheStartKeepsItsPartBefore() {
        val project = project(track(1, clip(1, 0.0)))
        val after = applied(ProjectEditor.apply(project, recordTake(trackId(1), start = 48.0, frames = 576_000)))
        val (before, take) = after.tracks[0].clips
        assertEquals(clipId(1), before.id)
        assertEquals(Beats(0.0), before.startBeats)
        assertEquals(0L, before.offsetFrames)
        assertEquals(1_152_000L, before.lengthFrames)
        assertEquals("loop", before.name)
        assertEquals(takeClipId, take.id)
        assertEquals(2, after.tracks[0].clips.size)
        assertNoOverlap(after, trackId(1))
    }

    @Test
    fun aClipStraddlingTheEndKeepsItsPartAfter() {
        val project = project(track(1, clip(1, 20.0)))
        val after = applied(ProjectEditor.apply(project, recordTake(trackId(1), start = 0.0, frames = 768_000)))
        val (take, rest) = after.tracks[0].clips
        assertEquals(takeClipId, take.id)
        assertEquals(clipId(1), rest.id)
        assertClose(32.0, rest.startBeats.value)
        assertEquals(288_000L, rest.offsetFrames)
        assertEquals(1_248_000L, rest.lengthFrames)
        assertEquals("loop", rest.name)
        assertEquals(2, after.tracks[0].clips.size)
        assertNoOverlap(after, trackId(1))
    }

    @Test
    fun aClipInsideTheSpanIsRemovedAndItsOrphanedAssetPruned() {
        val project = project(track(1, clip(1, 10.0, length = 240_000)))
        val after = applied(ProjectEditor.apply(project, recordTake(trackId(1), start = 0.0, frames = 768_000)))
        assertEquals(listOf(takeClipId), after.tracks[0].clips.map { it.id })
        assertNull(after.findClip(clipId(1)))
        assertEquals(setOf(takeAssetId), after.assets.keys)
    }

    @Test
    fun aClipCoveringExactlyTheSpanIsRemoved() {
        val project = project(track(1, clip(1, 10.0, length = 240_000)))
        val after = applied(ProjectEditor.apply(project, recordTake(trackId(1), start = 10.0, frames = 240_000)))
        assertEquals(listOf(takeClipId), after.tracks[0].clips.map { it.id })
    }

    @Test
    fun aClipStraddlingBothEndsKeepsBothOutsideParts() {
        val project = project(track(1, clip(1, 0.0)))
        val after = applied(ProjectEditor.apply(project, recordTake(trackId(1), start = 16.0, frames = 384_000)))
        val (left, take, right) = after.tracks[0].clips
        assertEquals(clipId(1), left.id)
        assertEquals(Beats(0.0), left.startBeats)
        assertEquals(0L, left.offsetFrames)
        assertEquals(384_000L, left.lengthFrames)
        assertEquals(takeClipId, take.id)
        assertEquals(ClipId("take-after-clip-1"), right.id)
        assertClose(32.0, right.startBeats.value)
        assertEquals(768_000L, right.offsetFrames)
        assertEquals(768_000L, right.lengthFrames)
        assertEquals("loop", right.name)
        assertEquals(stereoAssetId, right.assetId)
        assertEquals(3, after.tracks[0].clips.size)
        assertNoOverlap(after, trackId(1))
    }

    @Test
    fun aSecondTakeInsideTheKeptPartGetsItsOwnRightHandId() {
        val project = project(track(1, clip(1, 0.0)))
        val one = applied(ProjectEditor.apply(project, recordTake(trackId(1), start = 16.0, frames = 384_000, id = ClipId("t1"))))
        val two = applied(ProjectEditor.apply(one, recordTake(trackId(1), start = 4.0, frames = 96_000, id = ClipId("t2"), name = "Take 2")))
        assertEquals(listOf(clipId(1), ClipId("t2"), ClipId("t2-after-clip-1"), ClipId("t1"), ClipId("t1-after-clip-1")), two.tracks[0].clips.map { it.id })
        assertEquals(two.tracks[0].clips.size, two.tracks[0].clips.map { it.id }.toSet().size)
        assertNoOverlap(two, trackId(1))
    }

    @Test
    fun touchingClipsAreNotTrimmed() {
        val project = project(track(1, clip(1, 0.0, length = 240_000), clip(2, 20.0, length = 240_000)))
        val after = applied(ProjectEditor.apply(project, recordTake(trackId(1), start = 10.0, frames = 240_000)))
        assertEquals(listOf(clipId(1), takeClipId, clipId(2)), after.tracks[0].clips.map { it.id })
        assertEquals(project.findClip(clipId(1)), after.findClip(clipId(1)))
        assertEquals(project.findClip(clipId(2)), after.findClip(clipId(2)))
        assertNoOverlap(after, trackId(1))
    }

    @Test
    fun severalClipsAtOnceAreEachReducedToTheirOutsideParts() {
        val project = project(
            track(
                1,
                clip(1, 0.0, length = 240_000),
                clip(2, 12.0, length = 192_000),
                clip(3, 24.0, length = 144_000),
                clip(4, 34.0, length = 240_000),
            ),
        )
        val after = applied(ProjectEditor.apply(project, recordTake(trackId(1), start = 5.0, frames = 744_000)))
        assertEquals(listOf(clipId(1), takeClipId, clipId(4)), after.tracks[0].clips.map { it.id })
        val first = after.findClip(clipId(1))!!
        assertEquals(Beats(0.0), first.startBeats)
        assertEquals(0L, first.offsetFrames)
        assertEquals(120_000L, first.lengthFrames)
        val last = after.findClip(clipId(4))!!
        assertClose(36.0, last.startBeats.value)
        assertEquals(48_000L, last.offsetFrames)
        assertEquals(192_000L, last.lengthFrames)
        assertNull(after.findClip(clipId(2)))
        assertNull(after.findClip(clipId(3)))
        assertNoOverlap(after, trackId(1))
    }

    @Test
    fun aClipAtAnotherRateKeepsItsAudioAlignedAndNeverCrossesTheSpan() {
        val project = project(track(1, clip(1, 0.0, assetId = monoAssetId, length = 441_000)))
        val take = recordTake(trackId(1), start = 7.30003, frames = 123_457)
        val after = applied(ProjectEditor.apply(project, take))
        val (left, middle, right) = after.tracks[0].clips
        assertEquals(clipId(1), left.id)
        assertEquals(takeClipId, middle.id)
        assertEquals(ClipId("take-after-clip-1"), right.id)
        val takeStart = 7.30003
        val takeEnd = takeStart + 123_457.0 / 24_000
        assertTrue(after.clipEndBeats(left).value <= takeStart + 1e-9, "the left part ends inside the take")
        assertTrue(right.startBeats.value >= takeEnd - 1e-9, "the right part starts inside the take")
        assertEquals(0L, left.offsetFrames)
        assertClose(project.tempoMap.framesToBeats(right.offsetFrames, 44100).value, right.startBeats.value)
        assertEquals(441_000L, right.offsetFrames + right.lengthFrames)
        assertNoOverlap(after, trackId(1))
    }

    // --- Rejections --------------------------------------------------------------------------------------------------

    @Test
    fun aTakeShorterThanOneHundredthOfASecondIsRefused() {
        val project = project(track(1, clip(1, 0.0)))
        assertEquals(EditRejection.TakeTooShort, rejected(ProjectEditor.apply(project, recordTake(trackId(1), start = 0.0, frames = 479))))
        assertEquals(EditRejection.TakeTooShort, rejected(ProjectEditor.apply(project, recordTake(trackId(1), start = 0.0, frames = 1))))
        val shortest = applied(ProjectEditor.apply(project, recordTake(trackId(1), start = 0.0, frames = 480)))
        assertEquals(480L, shortest.findClip(takeClipId)!!.lengthFrames)
    }

    @Test
    fun aTakeOnAnUnknownTrackIsRefused() {
        val project = project(track(1))
        assertEquals(EditRejection.UnknownTrack(trackId(7)), rejected(ProjectEditor.apply(project, recordTake(trackId(7), start = 0.0, frames = 48_000))))
    }

    @Test
    fun aTakeIsRefusedPastTheClipBoundButAReplacementIsNot() {
        val tracks = (1..32).map { n -> track(n, *(1..32).map { c -> clip(n * 100 + c, (c - 1) * 70.0) }.toTypedArray()) }
        val project = project(*tracks.toTypedArray())
        assertEquals(1024, project.clipCount)
        assertEquals(EditRejection.ClipLimitReached, rejected(ProjectEditor.apply(project, recordTake(trackId(1), start = 5000.0, frames = 48_000))))
        val replaced = applied(ProjectEditor.apply(project, recordTake(trackId(1), start = 70.0, frames = 1_536_000)))
        assertEquals(1024, replaced.clipCount)
        assertNull(replaced.findClip(clipId(102)))
    }

    @Test
    fun aSplitIntoTwoPartsCountsTowardTheClipBound() {
        val tracks = (1..32).map { n -> track(n, *(1..32).map { c -> clip(n * 100 + c, (c - 1) * 70.0) }.toTypedArray()) }
        val project = project(*tracks.toTypedArray())
        assertEquals(EditRejection.ClipLimitReached, rejected(ProjectEditor.apply(project, recordTake(trackId(1), start = 16.0, frames = 384_000))))
    }

    @Test
    fun aRejectedTakeChangesNothing() {
        val project = project(track(1, clip(1, 0.0)))
        val result = ProjectEditor.apply(project, recordTake(trackId(1), start = 0.0, frames = 10))
        assertIs<EditResult.Rejected>(result)
        assertNull(project.findClip(takeClipId))
        assertEquals(setOf(stereoAssetId, monoAssetId), project.assets.keys)
    }

    // --- History -----------------------------------------------------------------------------------------------------

    @Test
    fun aTakeWithItsReplacementIsOneHistorySnapshotAndUndoRestoresEveryTrimmedClip() {
        val project = project(track(1, clip(1, 0.0, length = 240_000), clip(2, 12.0, length = 192_000), clip(3, 34.0, length = 240_000)))
        val after = applied(ProjectEditor.apply(project, recordTake(trackId(1), start = 5.0, frames = 744_000)))
        val history = EditHistory.start(project).push(after)
        assertEquals(1, history.past.size)
        assertEquals(after, history.present)
        val undone = history.undo()
        assertEquals(project, undone.present)
        assertNull(undone.present.findClip(takeClipId))
        assertEquals(after, undone.redo().present)
    }

    // --- clipsOverlapping --------------------------------------------------------------------------------------------

    @Test
    fun clipsOverlappingListsTheClipsCrossingTheSpanInStartOrderAndNotTouchingOnes() {
        val project = project(track(1, clip(1, 0.0, length = 240_000), clip(2, 10.0, length = 240_000), clip(3, 20.0, length = 240_000), clip(4, 40.0, length = 240_000)))
        assertEquals(listOf(clipId(2), clipId(3)), project.clipsOverlapping(trackId(1), Beats(10.0), Beats(30.0)).map { it.id })
        assertEquals(listOf(clipId(2)), project.clipsOverlapping(trackId(1), Beats(19.0), Beats(20.0)).map { it.id })
        assertEquals(listOf(clipId(1), clipId(2), clipId(3), clipId(4)), project.clipsOverlapping(trackId(1), Beats(9.0), Beats(41.0)).map { it.id })
        assertEquals(emptyList(), project.clipsOverlapping(trackId(1), Beats(30.0), Beats(40.0)))
        assertEquals(emptyList(), project.clipsOverlapping(trackId(1), Beats(50.0), Beats(60.0)))
        assertEquals(emptyList(), project.clipsOverlapping(trackId(9), Beats(0.0), Beats(100.0)))
    }

    @Test
    fun clipsOverlappingIgnoresAClipWhoseAssetIsMissing() {
        val project = project(track(1, clip(1, 0.0)), assets = emptyMap())
        assertEquals(emptyList(), project.clipsOverlapping(trackId(1), Beats(0.0), Beats(10.0)))
    }

    // --- Count-in ----------------------------------------------------------------------------------------------------

    @Test
    fun setCountInChangesOnlyTheCountIn() {
        val project = project(track(1, clip(1, 0.0)))
        assertEquals(CountIn.ONE_BAR, project.countIn)
        val off = applied(ProjectEditor.apply(project, SetCountIn(CountIn.OFF)))
        assertEquals(CountIn.OFF, off.countIn)
        assertEquals(project, off.copy(countIn = CountIn.ONE_BAR))
        assertEquals(CountIn.TWO_BARS, applied(ProjectEditor.apply(off, SetCountIn(CountIn.TWO_BARS))).countIn)
    }
}
