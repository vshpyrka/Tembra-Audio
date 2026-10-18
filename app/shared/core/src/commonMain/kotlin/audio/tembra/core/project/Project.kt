package audio.tembra.core.project

import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The project document's content (spec FR-003, FR-049; data-model.md section 1): the single source of truth the
 * engine derives its real-time view from (Constitution V). Everything musical is in beats; audio is referenced by
 * content id with its format facts; nothing here names a device path. Grid and zoom live in the device-side
 * `ProjectViewState`. Field order is the contract's wire order (and the future `@ProtoNumber` order). Name and
 * description length are rules of `ProjectEditor` and `ProjectValidator`, not of the constructor.
 */
@Serializable
data class Project(
    @SerialName("id") val id: ProjectId,
    @SerialName("name") val name: String,
    @SerialName("description") val description: String = "",
    @SerialName("key") @Serializable(with = MusicalKeySerializer::class) val key: MusicalKey? = null,
    @SerialName("tempo") @Serializable(with = TempoSerializer::class) val tempo: Tempo,
    @SerialName("timeSignature") @Serializable(with = TimeSignatureSerializer::class) val timeSignature: TimeSignature,
    @SerialName("masterVolume") val masterVolume: Percent = Percent(DEFAULT_MASTER_VOLUME),
    @SerialName("metronomeOn") val metronomeOn: Boolean = false,
    @SerialName("assets") @Serializable(with = PersistentMapSerializer::class) val assets: PersistentMap<AudioAssetId, AudioAsset>,
    @SerialName("tracks") @Serializable(with = PersistentListSerializer::class) val tracks: PersistentList<Track>,
) {
    companion object {
        const val DEFAULT_MASTER_VOLUME = 80

        /** A new project as spec FR-003 and FR-052 define it: 120 BPM, 4/4, no description, no key, no tracks. */
        fun new(id: ProjectId, name: String): Project = Project(
            id = id,
            name = name,
            tempo = Tempo.Default,
            timeSignature = TimeSignature.Default,
            assets = persistentMapOf(),
            tracks = persistentListOf(),
        )
    }
}
