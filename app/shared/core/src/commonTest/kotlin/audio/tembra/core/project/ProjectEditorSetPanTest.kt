package audio.tembra.core.project

import audio.tembra.core.project.ProjectFixtures.project
import audio.tembra.core.project.ProjectFixtures.track
import audio.tembra.core.project.ProjectFixtures.trackId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** The pan edit (feature 012, spec FR-005 to FR-008): one track's pan, rejected for an unknown track or an out-of-range value. */
class ProjectEditorSetPanTest {

    private val project = project(track(1), track(2))

    private fun applied(result: EditResult): Project = assertIs<EditResult.Applied>(result).project

    private fun rejected(result: EditResult): EditRejection = assertIs<EditResult.Rejected>(result).reason

    @Test
    fun aNewTrackIsCentred() {
        assertEquals(Pan.Centre, Track.new(TrackId("n"), "Track 1", 0).pan)
        assertEquals(Pan.Centre, project.tracks[0].pan)
    }

    @Test
    fun setPanChangesOneTrackAndNothingElse() {
        val next = applied(ProjectEditor.apply(project, SetPan(trackId(2), Pan(-35))))
        assertEquals(Pan(-35), next.tracks[1].pan)
        assertEquals(Pan.Centre, next.tracks[0].pan)
        assertEquals(project.tracks[1].copy(pan = Pan(-35)), next.tracks[1])
        assertEquals(project.copy(tracks = next.tracks), next)
    }

    @Test
    fun anUnknownTrackIsRejected() {
        assertEquals(EditRejection.UnknownTrack(TrackId("x")), rejected(ProjectEditor.apply(project, SetPan(TrackId("x"), Pan(10)))))
    }

    @Test
    fun aPanOutsideTheRangeIsRejected() {
        assertEquals(EditRejection.PanOutOfRange, rejected(ProjectEditor.apply(project, SetPan(trackId(1), Pan(101)))))
        assertEquals(EditRejection.PanOutOfRange, rejected(ProjectEditor.apply(project, SetPan(trackId(1), Pan(-101)))))
        assertEquals(Pan(100), applied(ProjectEditor.apply(project, SetPan(trackId(1), Pan(100)))).tracks[0].pan)
    }

    @Test
    fun theExtremesAndTheCentreRoundTripThroughTheEditor() {
        var current = project
        for (pan in listOf(Pan(-100), Pan(100), Pan.Centre, Pan(7))) {
            current = applied(ProjectEditor.apply(current, SetPan(trackId(1), pan)))
            assertEquals(pan, current.tracks[0].pan)
        }
    }
}
