package audio.tembra.ui.drop

import androidx.compose.ui.geometry.Offset
import audio.tembra.core.project.Beats
import audio.tembra.core.project.TrackId
import audio.tembra.data.projects.PickedFile
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A drop zone's state while files hover and land (feature 011, spec FR-026; contracts/drop-import.md behaviour 2–4): the
 * hover names what a drop would do where the pointer is (a track's lane, the space below, Projects), follows the
 * pointer, and clears when the drag leaves or ends; the drop plans the items it reads at the drop position.
 */
class AudioDropStateTest {

    private val drums = TrackId("drums")

    /**
     * Rows 0–99 px are the Drums lane, below is space; x / 100 is the beat, snapped down to a whole beat. Without a
     * position (iOS) it is the space at beat 12, where the playhead stands.
     */
    private val state = AudioDropState(
        zoneAt = { position -> if (position != null && position.y < 100f) DropZone.TrackLane(drums) else DropZone.TimelineSpace },
        beatsAt = { position -> if (position == null) Beats(12.0) else Beats(kotlin.math.floor(position.x / 100.0)) },
    )

    private fun audio(name: String) = DroppedItem.AudioFile(PickedFile(name) { error("not read") })

    @Test
    fun oneFileOverALaneOffersToJoinThatTrackAtTheBeatUnderThePointer() {
        state.entered(Offset(450f, 50f), audioCount = 1)
        assertEquals(DropHover(DropZone.TrackLane(drums), DropPreview.AddToTrack(drums, Beats(4.0))), state.hover)
        state.moved(Offset(820f, 50f))
        assertEquals(DropPreview.AddToTrack(drums, Beats(8.0)), state.hover?.preview)
    }

    @Test
    fun severalFilesOverALaneOrAnyFilesOverTheSpaceOfferNewTracks() {
        state.entered(Offset(450f, 50f), audioCount = 3)
        assertEquals(DropPreview.NewTracks(Beats(4.0)), state.hover?.preview)
        state.moved(Offset(200f, 300f))
        assertEquals(DropHover(DropZone.TimelineSpace, DropPreview.NewTracks(Beats(2.0))), state.hover)
    }

    @Test
    fun anUnknownCountOverALaneOffersToJoinTheTrack() {
        // Android names the types before the drop but not how many items there are.
        state.entered(Offset(100f, 10f), audioCount = null)
        assertEquals(DropPreview.AddToTrack(drums, Beats(1.0)), state.hover?.preview)
    }

    @Test
    fun withoutAPositionTheZoneDecides() {
        // Compose Multiplatform 1.12 does not tell iOS where the drag is: the timeline offers new tracks at the playhead.
        state.entered(position = null, audioCount = 1)
        assertEquals(DropHover(DropZone.TimelineSpace, DropPreview.NewTracks(Beats(12.0))), state.hover)
    }

    @Test
    fun projectsOffersANewProject() {
        val projects = AudioDropState(zoneAt = { DropZone.ProjectsGrid }, beatsAt = { Beats.Zero })
        projects.entered(Offset(10f, 10f), audioCount = 2)
        assertEquals(DropHover(DropZone.ProjectsGrid, DropPreview.NewProject), projects.hover)
    }

    @Test
    fun leavingOrEndingClearsTheHover() {
        state.entered(Offset(10f, 10f), audioCount = 1)
        state.exited()
        assertNull(state.hover)
        state.entered(Offset(10f, 10f), audioCount = 1)
        state.ended()
        assertNull(state.hover)
    }

    @Test
    fun aDropPlansWhatItReadsAtTheDropPosition() = runTest {
        state.entered(Offset(450f, 50f), audioCount = 1)
        val outcome = state.dropped(Offset(650f, 50f)) { listOf(audio("kick.wav"), DroppedItem.Other("notes.txt")) }
        val plan = outcome.plan as DropPlan.AddToTrack
        assertEquals(drums, plan.trackId)
        assertEquals(Beats(6.0), plan.atBeats)
        assertEquals(listOf(SkippedItem("notes.txt", SkipReason.NotAudio)), outcome.skipped)
        assertNull(state.hover)
    }
}
