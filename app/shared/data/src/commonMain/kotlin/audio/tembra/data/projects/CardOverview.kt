package audio.tembra.data.projects

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The library card's mix overview (spec FR-054): [spans] equal slices of the project, each with a peak 0..255
 * (unsigned in [peaks]) and the palette index of the loudest track in [colors]. Both columns travel as base64 in
 * `library.json`. [Empty] is the overview of a project without clips.
 */
@Serializable
class CardOverview(
    @SerialName("spans") val spans: Int,
    @SerialName("peaks") @Serializable(with = Base64ByteArraySerializer::class) val peaks: ByteArray,
    @SerialName("colors") @Serializable(with = Base64ByteArraySerializer::class) val colors: ByteArray,
) {
    init {
        require(peaks.size == spans && colors.size == spans) { "A card overview needs one peak and one colour per span" }
    }

    override fun equals(other: Any?): Boolean =
        other is CardOverview && other.spans == spans && other.peaks.contentEquals(peaks) && other.colors.contentEquals(colors)

    override fun hashCode(): Int = 31 * (31 * spans + peaks.contentHashCode()) + colors.contentHashCode()

    override fun toString(): String = "CardOverview(spans=$spans)"

    companion object {
        val Empty: CardOverview = CardOverview(0, ByteArray(0), ByteArray(0))
    }
}
