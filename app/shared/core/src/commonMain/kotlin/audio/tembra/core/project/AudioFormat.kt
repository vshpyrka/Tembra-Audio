package audio.tembra.core.project

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The container of an imported audio copy (spec FR-044): the three the engine's decoders read. Serialised as its
 * lower-case name; a reader that meets an unknown value refuses the asset as unavailable
 * (contracts/project-document.md, evolution rule 7).
 */
@Serializable
enum class AudioFormat(val extension: String) {
    @SerialName("wav")
    Wav("wav"),

    @SerialName("flac")
    Flac("flac"),

    @SerialName("mp3")
    Mp3("mp3");

    companion object {
        /** Maps the C ABI's `tembra_audio_format`; 0 (unknown) and anything else read as null. */
        fun fromNative(value: Int): AudioFormat? = when (value) {
            1 -> Wav
            2 -> Flac
            3 -> Mp3
            else -> null
        }
    }
}
