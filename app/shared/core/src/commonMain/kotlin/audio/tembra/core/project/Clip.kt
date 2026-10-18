package audio.tembra.core.project

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A contiguous piece of one audio copy on one track (spec FR-027): a start in beats, an offset into the audio and a
 * length, both in frames at the asset's own sample rate, and a name (the file name without extension, FR-030). The
 * tempo never changes how fast a clip plays. Field order is the contract's wire order.
 */
@Serializable
data class Clip(
    @SerialName("id") val id: ClipId,
    @SerialName("assetId") val assetId: AudioAssetId,
    @SerialName("startBeats") val startBeats: Beats,
    @SerialName("offsetFrames") val offsetFrames: Long,
    @SerialName("lengthFrames") val lengthFrames: Long,
    @SerialName("name") val name: String,
) {
    init {
        require(lengthFrames > 0) { "A clip must be at least one frame long, was $lengthFrames" }
        require(offsetFrames >= 0) { "A clip's offset must not be negative, was $offsetFrames" }
        require(startBeats.value >= 0.0) { "A clip must not start before the project, was ${startBeats.value}" }
    }

    /** True when `offset + length ≤ asset.frames`. */
    fun fitsWithin(asset: AudioAsset): Boolean = offsetFrames + lengthFrames <= asset.frames
}
