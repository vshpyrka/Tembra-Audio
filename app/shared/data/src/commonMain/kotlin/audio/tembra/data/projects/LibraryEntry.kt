package audio.tembra.data.projects

import audio.tembra.core.metronome.Tempo
import audio.tembra.core.project.LibrarySummary
import audio.tembra.core.project.MusicalKey
import audio.tembra.core.project.MusicalKeySerializer
import audio.tembra.core.project.ProjectId
import audio.tembra.core.project.TempoSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One card of `library.json` (contracts/project-storage.md): the project's card facts cached from its document plus
 * the device-side size, last-edited time, origin and mix overview. [unavailable] carries the reason when the
 * document could not be read, so the library still lists it.
 */
@Serializable
data class LibraryEntry(
    @SerialName("id") val id: ProjectId,
    @SerialName("name") val name: String,
    @SerialName("description") val description: String,
    @SerialName("key") @Serializable(with = MusicalKeySerializer::class) val key: MusicalKey?,
    @SerialName("tempo") @Serializable(with = TempoSerializer::class) val tempo: Tempo,
    @SerialName("lengthSeconds") val lengthSeconds: Double,
    @SerialName("trackCount") val trackCount: Int,
    @SerialName("byteSize") val byteSize: Long,
    @SerialName("lastEdited") val lastEdited: Long,
    @SerialName("origin") val origin: ProjectOrigin,
    @SerialName("overview") val overview: CardOverview,
    @SerialName("unavailable") val unavailable: String? = null,
) {
    /** The pure fields `LibraryQuery` filters, sorts and searches. */
    fun toSummary(): LibrarySummary = LibrarySummary(
        id = id, name = name, description = description, key = key, tempo = tempo, lengthSeconds = lengthSeconds,
        trackCount = trackCount, byteSize = byteSize, lastEditedEpochMs = lastEdited, isDemo = origin == ProjectOrigin.Demo,
    )
}
