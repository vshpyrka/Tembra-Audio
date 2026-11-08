package audio.tembra.ui.projects

import audio.tembra.core.project.ProjectId

/**
 * The stress project's fixed identity (feature 012, contracts/perf-instrumentation.md): one id, so the journeys find
 * its card at `projects.card.stressTracks` (`PerfAnchors.PROJECTS_CARD_STRESS`) and a later launch finds the project
 * already built; its name carries the track count.
 */
object StressProject {
    val ID = ProjectId("stressTracks")

    fun name(trackCount: Int): String = "Stress project ($trackCount tracks)"
}
