package audio.tembra.data.projects

import audio.tembra.core.project.AudioAssetId
import audio.tembra.core.project.ClipId
import audio.tembra.core.project.Pan
import audio.tembra.core.project.TrackId
import kotlinx.collections.immutable.toPersistentList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Every named reason a document opens as "unavailable project" (contracts/project-document.md, validation on read). */
class ProjectValidatorTest {

    private val assetId = AudioAssetId("c".repeat(64))
    private val assets = mapOf(assetId to DataFixtures.stereoAsset())

    @Test
    fun aValidProjectHasNoFailures() {
        assertEquals(emptyList(), ProjectValidator.validate(DataFixtures.manyTracks(3)))
    }

    @Test
    fun duplicateTrackAndClipIds() {
        val tracks = listOf(DataFixtures.track(1, DataFixtures.clip(1, assetId)), DataFixtures.track(1, DataFixtures.clip(1, assetId, start = 100.0)))
        assertEquals(
            listOf(ValidationFailure.DuplicateId("track-1"), ValidationFailure.DuplicateId("clip-1")),
            ProjectValidator.validate(DataFixtures.project(tracks = tracks, assets = assets)),
        )
    }

    @Test
    fun aClipWhoseAssetIsMissingFromTheDocument() {
        val project = DataFixtures.project(tracks = listOf(DataFixtures.track(1, DataFixtures.clip(1, AudioAssetId("d".repeat(64))))), assets = assets)
        assertEquals(listOf(ValidationFailure.MissingAsset(ClipId("clip-1"), AudioAssetId("d".repeat(64)))), ProjectValidator.validate(project))
    }

    @Test
    fun aClipPastTheEndOfItsAsset() {
        val project = DataFixtures.project(tracks = listOf(DataFixtures.track(1, DataFixtures.clip(1, assetId, offset = 1, length = 1_536_000))), assets = assets)
        assertEquals(listOf(ValidationFailure.ClipOutsideAsset(ClipId("clip-1"))), ProjectValidator.validate(project))
    }

    @Test
    fun tempoAndSignatureOutOfRange() {
        assertEquals(ValidationFailure.TempoOutOfRange(300), ProjectValidator.validateTempo(300))
        assertEquals(ValidationFailure.TempoOutOfRange(39), ProjectValidator.validateTempo(39))
        assertNull(ProjectValidator.validateTempo(40))
        assertNull(ProjectValidator.validateTempo(240))
        assertEquals(ValidationFailure.UnknownTimeSignature("9/8"), ProjectValidator.validateTimeSignature("9/8"))
        assertNull(ProjectValidator.validateTimeSignature("7/8"))
    }

    @Test
    fun theEngineBounds() {
        assertEquals(listOf(ValidationFailure.TooManyTracks(65)), ProjectValidator.validate(DataFixtures.manyTracks(65)))
        val clips = (1..1025).map { DataFixtures.clip(it, assetId, start = (it - 1) * 70.0) }
        val project = DataFixtures.project(tracks = listOf(DataFixtures.track(1, *clips.toTypedArray())), assets = assets)
        assertEquals(listOf(ValidationFailure.TooManyClips(1025)), ProjectValidator.validate(project))
    }

    @Test
    fun namesOutOfLength() {
        assertEquals(listOf(ValidationFailure.ProjectNameLength(61)), ProjectValidator.validate(DataFixtures.project(name = "x".repeat(61))))
        assertEquals(listOf(ValidationFailure.ProjectNameLength(0)), ProjectValidator.validate(DataFixtures.project(name = "  ")))
        assertEquals(
            listOf(ValidationFailure.DescriptionLength(121)),
            ProjectValidator.validate(DataFixtures.project().copy(description = "x".repeat(121))),
        )
        val longTrack = DataFixtures.project(tracks = listOf(DataFixtures.track(1, name = "x".repeat(41))))
        assertEquals(listOf(ValidationFailure.TrackNameLength(TrackId("track-1"), 41)), ProjectValidator.validate(longTrack))
        val blankTrack = DataFixtures.project(tracks = listOf(DataFixtures.track(1, name = "")))
        assertEquals(listOf(ValidationFailure.TrackNameLength(TrackId("track-1"), 0)), ProjectValidator.validate(blankTrack))
    }

    @Test
    fun assetFactsMustBePlausible() {
        val bad = DataFixtures.stereoAsset().copy(channels = 4)
        val project = DataFixtures.project(assets = mapOf(assetId to bad), tracks = listOf(DataFixtures.track(1, DataFixtures.clip(1, assetId))).toPersistentList())
        assertEquals(listOf(ValidationFailure.AssetFactsInvalid(assetId)), ProjectValidator.validate(project))
    }

    @Test
    fun aPanOutsideTheRangeIsReported() {
        // Feature 012 (spec FR-006): the constructor accepts the value so the document can be reported, the validator refuses it.
        val tracks = listOf(DataFixtures.track(1).copy(pan = Pan(101)), DataFixtures.track(2).copy(pan = Pan(-100)), DataFixtures.track(3).copy(pan = Pan(-101)))
        assertEquals(
            listOf(ValidationFailure.PanOutOfRange(TrackId("track-1"), 101), ValidationFailure.PanOutOfRange(TrackId("track-3"), -101)),
            ProjectValidator.validate(DataFixtures.project(tracks = tracks, assets = assets)),
        )
    }

    @Test
    fun failuresHaveMessages() {
        assertEquals("Duplicate id \"track-1\"", ValidationFailure.DuplicateId("track-1").message)
        assertEquals("Tempo 300 is outside 40..240", ValidationFailure.TempoOutOfRange(300).message)
        assertEquals("Track track-1 has a pan of 140, outside −100..100", ValidationFailure.PanOutOfRange(TrackId("track-1"), 140).message)
    }
}
