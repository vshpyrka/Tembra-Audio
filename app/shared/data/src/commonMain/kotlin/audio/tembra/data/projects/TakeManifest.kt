package audio.tembra.data.projects

import audio.tembra.core.project.Beats
import audio.tembra.core.project.TrackId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * `audio/.take-<token>.json`, written by the recording controller before capture begins (contracts/take-storage.md;
 * research R14): the only way to know, after a crash, which track and position the staged file belongs to. [token]
 * names both staging files, [startBeats] is the Record position (or the pre-roll's start), [takeName] was chosen at
 * record start, [startedAt] is epoch milliseconds, [alignmentFrames] the round-trip latency the engine discards at the
 * start. Parses and prints itself; unknown fields are ignored so a later version's manifest still reads.
 */
@Serializable
data class TakeManifest(
    @SerialName("token") val token: String,
    @SerialName("trackId") val trackId: TrackId,
    @SerialName("startBeats") val startBeats: Beats,
    @SerialName("takeName") val takeName: String,
    @SerialName("startedAt") val startedAt: Long,
    @SerialName("alignmentFrames") val alignmentFrames: Long,
    @SerialName("sampleRate") val sampleRate: Int,
    @SerialName("channels") val channels: Int,
    @SerialName("formatVersion") val formatVersion: Int = CURRENT_VERSION,
) {
    /** The manifest text, ending with a newline like the document. */
    fun toJson(): String = json.encodeToString(serializer(), this) + "\n"

    companion object {
        const val CURRENT_VERSION = 1

        private val json: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = true
        }

        /** The manifest in [text]; null for anything malformed, which the repository treats as a take nothing can recover. */
        fun parse(text: String): TakeManifest? = try {
            json.decodeFromString(serializer(), text)
        } catch (failure: SerializationException) {
            null
        } catch (failure: IllegalArgumentException) {
            null
        }
    }
}
