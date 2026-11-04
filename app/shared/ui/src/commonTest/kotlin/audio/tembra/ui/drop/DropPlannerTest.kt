package audio.tembra.ui.drop

import audio.tembra.core.project.Beats
import audio.tembra.core.project.TrackId
import audio.tembra.data.projects.PickedFile
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What a drop does (feature 011, spec FR-026; data-model.md "Drops"): exactly one audio file on a track's lane joins
 * that track at the snapped beat; several files, or files on the timeline's empty space, become new tracks there; files
 * on Projects become a new project; a readable folder gives its direct audio files (its sub-folders are not entered);
 * whatever is not audio, and a folder the platform does not let the app read, is named in the skipped list.
 */
class DropPlannerTest {

    private val drums = TrackId("drums")
    private val bar5 = Beats(16.0)

    private fun audio(name: String) = DroppedItem.AudioFile(PickedFile(name) { error("a plan never reads the bytes") })

    private fun names(plan: DropPlan): List<String> = when (plan) {
        is DropPlan.AddToTrack -> listOf(plan.file.displayName)
        is DropPlan.NewTracks -> plan.files.map { it.displayName }
        is DropPlan.NewProject -> plan.files.map { it.displayName }
        DropPlan.Nothing -> emptyList()
    }

    @Test
    fun exactlyOneAudioFileOnALaneJoinsThatTrackAtTheSnappedBeat() {
        val outcome = DropPlanner.plan(DropZone.TrackLane(drums), listOf(audio("kick.wav")), bar5)
        val plan = outcome.plan as DropPlan.AddToTrack
        assertEquals(drums, plan.trackId)
        assertEquals(bar5, plan.atBeats)
        assertEquals("kick.wav", plan.file.displayName)
        assertEquals(emptyList(), outcome.skipped)
    }

    @Test
    fun twoFilesOnALaneBecomeNewTracks() {
        val outcome = DropPlanner.plan(DropZone.TrackLane(drums), listOf(audio("a.wav"), audio("b.wav")), bar5)
        assertEquals(DropPlan.NewTracks::class, outcome.plan::class)
        assertEquals(bar5, (outcome.plan as DropPlan.NewTracks).atBeats)
        assertEquals(listOf("a.wav", "b.wav"), names(outcome.plan))
    }

    @Test
    fun filesOnTheEmptySpaceBecomeNewTracks() {
        val outcome = DropPlanner.plan(DropZone.TimelineSpace, listOf(audio("a.wav")), bar5)
        assertEquals(DropPlan.NewTracks::class, outcome.plan::class)
        assertEquals(listOf("a.wav"), names(outcome.plan))
    }

    @Test
    fun filesOnProjectsBecomeANewProject() {
        val outcome = DropPlanner.plan(DropZone.ProjectsGrid, listOf(audio("a.wav"), audio("b.flac")), Beats(0.0))
        assertEquals(DropPlan.NewProject::class, outcome.plan::class)
        assertEquals(listOf("a.wav", "b.flac"), names(outcome.plan))
    }

    @Test
    fun aReadableFolderGivesItsDirectAudioFilesOnly() {
        val folder = DroppedItem.Folder(
            name = "Stems",
            contents = listOf(audio("1.wav"), audio("2.wav"), audio("3.wav"), DroppedItem.Other("notes.txt"), DroppedItem.Folder("Old", listOf(audio("x.wav")))),
        )
        val outcome = DropPlanner.plan(DropZone.TimelineSpace, listOf(folder), bar5)
        assertEquals(listOf("1.wav", "2.wav", "3.wav"), names(outcome.plan))
        assertEquals(listOf(SkippedItem("notes.txt", SkipReason.NotAudio), SkippedItem("Old", SkipReason.NestedFolder)), outcome.skipped)
    }

    @Test
    fun anUnreadableFolderIsNamedAsSkipped() {
        val outcome = DropPlanner.plan(DropZone.TimelineSpace, listOf(DroppedItem.Folder("Stems", contents = null), audio("a.wav")), bar5)
        assertEquals(listOf("a.wav"), names(outcome.plan))
        assertEquals(listOf(SkippedItem("Stems", SkipReason.UnreadableFolder)), outcome.skipped)
    }

    @Test
    fun onlyNonAudioPlansNothing() {
        val outcome = DropPlanner.plan(DropZone.TrackLane(drums), listOf(DroppedItem.Other("notes.txt"), DroppedItem.Other("cover.png")), bar5)
        assertEquals(DropPlan.Nothing, outcome.plan)
        assertEquals(listOf("notes.txt", "cover.png"), outcome.skipped.map { it.name })
    }
}
