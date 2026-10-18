package audio.tembra.data.projects

import audio.tembra.core.project.ClipId
import audio.tembra.core.project.IdGenerator
import audio.tembra.core.project.ProjectId
import audio.tembra.core.project.TrackId

/** The ten identities of the demo document: fresh for every install, fixed for the golden fixture. */
data class DemoProjectIds(val projectId: ProjectId, val trackIds: List<TrackId>, val clipIds: List<ClipId>) {
    init {
        require(trackIds.size == DemoProject.LOOPS.size && clipIds.size == DemoProject.CLIP_COUNT) { "The demo has ${DemoProject.LOOPS.size} tracks and ${DemoProject.CLIP_COUNT} clips" }
    }

    companion object {
        /** Fresh track and clip ids for a project identified by [projectId] (its folder's id by default a new one). */
        fun random(projectId: ProjectId = IdGenerator.projectId()): DemoProjectIds = DemoProjectIds(
            projectId = projectId,
            trackIds = List(DemoProject.LOOPS.size) { IdGenerator.trackId() },
            clipIds = List(DemoProject.CLIP_COUNT) { IdGenerator.clipId() },
        )

        /** The ids of `specs/009-timeline-arranger/fixtures/demo-project.json`. */
        fun fixed(): DemoProjectIds = DemoProjectIds(
            projectId = ProjectId("0b6f1c9a-4d2e-4f7a-9c1b-2e5d8a7f3c10"),
            trackIds = List(DemoProject.LOOPS.size) { TrackId("7d1c0a2e-0000-4000-8000-${(it + 1).toString().padStart(12, '0')}") },
            clipIds = List(DemoProject.CLIP_COUNT) { ClipId("9e4f6b1d-0000-4000-8000-${(it + 1).toString().padStart(12, '0')}") },
        )
    }
}
