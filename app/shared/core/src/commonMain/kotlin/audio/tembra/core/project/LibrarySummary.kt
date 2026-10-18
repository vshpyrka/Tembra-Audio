package audio.tembra.core.project

import audio.tembra.core.metronome.Tempo

/** The pure card fields of one library project that [LibraryQuery] filters, sorts and searches (spec FR-051). */
data class LibrarySummary(
    val id: ProjectId,
    val name: String,
    val description: String,
    val key: MusicalKey?,
    val tempo: Tempo,
    val lengthSeconds: Double,
    val trackCount: Int,
    val byteSize: Long,
    val lastEditedEpochMs: Long,
    val isDemo: Boolean,
)
