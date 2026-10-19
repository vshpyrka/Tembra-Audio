package audio.tembra.ui.projects

import androidx.compose.runtime.Immutable
import audio.tembra.core.project.MusicalKey
import audio.tembra.core.project.ProjectId
import audio.tembra.data.projects.CardOverview

/** One card as the library draws it (spec FR-051): the texts already formatted, the overview, the badges. */
@Immutable
data class ProjectCardUi(
    val id: ProjectId,
    val name: String,
    val description: String,
    val tempoText: String,
    val keyText: String,
    val lengthText: String,
    val tracksText: String,
    val sizeText: String,
    val relativeTime: String,
    val isActive: Boolean,
    val isDemo: Boolean,
    val overview: CardOverview,
    /** The key itself, for the key dialog; [keyText] is what the card shows. */
    val key: MusicalKey? = null,
) {
    /** "Demo project, Four loops to play with, 124 BPM, Dmin, 00:30.96, 4 tracks, 2 m ago" (FR-058). */
    val accessibilityDescription: String get() = buildList {
        add(name)
        if (description.isNotBlank()) add(description)
        add(tempoText)
        add(keyText)
        add(lengthText)
        add(tracksText)
        add(relativeTime)
        if (isActive) add(ProjectsCopy.active)
        if (isDemo) add(ProjectsCopy.demo)
    }.joinToString(", ")
}
