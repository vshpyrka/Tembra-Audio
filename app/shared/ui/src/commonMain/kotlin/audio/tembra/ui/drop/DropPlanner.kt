package audio.tembra.ui.drop

import audio.tembra.core.project.Beats
import audio.tembra.data.projects.PickedFile

/**
 * Resolves what a drop does (feature 011, spec FR-026; data-model.md "Drops"): readable folders give their direct
 * children (one level; a folder inside is named, not entered); exactly one audio file on a lane joins that track at
 * [plan]'s snapped beat; otherwise lanes and the empty space make new tracks there; Projects makes a new project; with no
 * audio file at all, nothing happens. Everything left out is listed in [DropOutcome.skipped], in drop order.
 */
object DropPlanner {
    fun plan(zone: DropZone, items: List<DroppedItem>, snappedBeats: Beats): DropOutcome {
        val files = mutableListOf<PickedFile>()
        val skipped = mutableListOf<SkippedItem>()
        for (item in items) {
            when (item) {
                is DroppedItem.AudioFile -> files += item.file
                is DroppedItem.Other -> skipped += SkippedItem(item.name, SkipReason.NotAudio)
                is DroppedItem.Folder -> {
                    val contents = item.contents
                    if (contents == null) {
                        skipped += SkippedItem(item.name, SkipReason.UnreadableFolder)
                    } else {
                        for (child in contents) {
                            when (child) {
                                is DroppedItem.AudioFile -> files += child.file
                                is DroppedItem.Other -> skipped += SkippedItem(child.name, SkipReason.NotAudio)
                                is DroppedItem.Folder -> skipped += SkippedItem(child.name, SkipReason.NestedFolder)
                            }
                        }
                    }
                }
            }
        }
        return DropOutcome(planFor(zone, files, snappedBeats), skipped)
    }

    /**
     * The hover's preview: one audio file over a lane joins the track, and so does an [audioCount] the platform does not
     * tell before the drop (Android; the drop decides); more files, or the space, make new tracks; Projects a project.
     */
    fun preview(zone: DropZone, audioCount: Int?, snappedBeats: Beats): DropPreview = when (zone) {
        is DropZone.TrackLane -> if (audioCount == null || audioCount == 1) DropPreview.AddToTrack(zone.trackId, snappedBeats) else DropPreview.NewTracks(snappedBeats)
        DropZone.TimelineSpace -> DropPreview.NewTracks(snappedBeats)
        DropZone.ProjectsGrid -> DropPreview.NewProject
    }

    private fun planFor(zone: DropZone, files: List<PickedFile>, snappedBeats: Beats): DropPlan = when {
        files.isEmpty() -> DropPlan.Nothing
        zone is DropZone.TrackLane && files.size == 1 -> DropPlan.AddToTrack(zone.trackId, snappedBeats, files.single())
        zone is DropZone.TrackLane || zone == DropZone.TimelineSpace -> DropPlan.NewTracks(files, snappedBeats)
        else -> DropPlan.NewProject(files)
    }
}
