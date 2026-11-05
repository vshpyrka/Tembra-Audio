package audio.tembra.core.project

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A track (spec FR-019 to FR-024): a name, an optional instrument label, one of the six palette colours, a gain, the
 * mute and solo toggles, its clips in start order and, since feature 012 (spec FR-006), its pan. Name length and the
 * pan's range are rules of `ProjectEditor` (rejecting) and `ProjectValidator` (reporting), not of the constructor, so
 * a document with a bad name or pan can still be reported. Field order is the contract's wire order: `pan` was
 * appended after `clips` with a default, so a document from before feature 012 reads as centred.
 */
@Serializable
data class Track(
    @SerialName("id") val id: TrackId,
    @SerialName("name") val name: String,
    @SerialName("label") @Serializable(with = InstrumentLabelSerializer::class) val label: InstrumentLabel? = null,
    @SerialName("colorIndex") val colorIndex: Int,
    @SerialName("gainDb") val gainDb: GainDb = GainDb.Unity,
    @SerialName("muted") val muted: Boolean = false,
    @SerialName("soloed") val soloed: Boolean = false,
    @SerialName("clips") @Serializable(with = PersistentListSerializer::class) val clips: PersistentList<Clip> = persistentListOf(),
    @SerialName("pan") val pan: Pan = Pan.Centre,
) {
    init {
        require(colorIndex in 0 until PALETTE_SIZE) { "colorIndex must be 0..${PALETTE_SIZE - 1}, was $colorIndex" }
    }

    /** "Take N" with N one more than the highest "Take N" among this track's clip names, starting at 1 (feature 010, spec FR-012). */
    fun nextTakeName(): String {
        val highest = clips.mapNotNull { takeNumbered.matchEntire(it.name)?.groupValues?.get(1)?.toIntOrNull() }.maxOrNull() ?: 0
        return "Take ${highest + 1}"
    }

    companion object {
        /** The design's track palette has six tints (spec FR-022). */
        const val PALETTE_SIZE = 6

        private val numbered = Regex("Track (\\d+)")

        private val takeNumbered = Regex("Take (\\d+)")

        /** A track at the defaults of spec FR-019: no label, 0 dB, not muted, not soloed, no clips. */
        fun new(id: TrackId, name: String, colorIndex: Int): Track = Track(id = id, name = name, colorIndex = colorIndex)

        /** "Track N" with N one more than the highest "Track N" among [existingNames], starting at 1. */
        fun nextName(existingNames: Iterable<String>): String {
            val highest = existingNames.mapNotNull { numbered.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }.maxOrNull() ?: 0
            return "Track ${highest + 1}"
        }
    }
}
