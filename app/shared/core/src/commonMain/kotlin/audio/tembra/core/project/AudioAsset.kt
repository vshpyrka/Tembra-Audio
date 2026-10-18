package audio.tembra.core.project

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The immutable facts of an audio copy (the spec's "Audio Copy"; data-model.md section 1), keyed in the document by
 * its [AudioAssetId]. [format] is null only when the document named a container this reader does not know
 * (contracts/project-document.md, evolution rule 7): the asset is then unavailable — its clips draw greyed and never
 * play — while the document itself stays readable. Field order is the contract's wire order.
 */
@Serializable
data class AudioAsset(
    @SerialName("fileName") val fileName: String,
    @SerialName("format") @Serializable(with = LenientAudioFormatSerializer::class) val format: AudioFormat?,
    @SerialName("channels") val channels: Int,
    @SerialName("sampleRate") val sampleRate: Int,
    @SerialName("frames") val frames: Long,
    @SerialName("byteSize") val byteSize: Long,
) {
    val durationSeconds: Double get() = frames.toDouble() / sampleRate

    /** False when the format is unknown to this reader. */
    val isAvailable: Boolean get() = format != null

    /** `<sha256>.<ext>`, the name of the copy inside the project's `audio/` folder; null when the format is unknown. */
    fun storedFileName(id: AudioAssetId): String? = format?.let { "${id.sha256}.${it.extension}" }
}
