package audio.tembra.ui.tuner

/** What a By Ear card plays: the built-in tone, or a bundled recording that replaces it (spec FR-022). */
sealed interface ReferenceSound {
    data class Synth(val frequencyHz: Double) : ReferenceSound

    data class Recording(val resourceName: String, val bytes: ByteArray) : ReferenceSound {
        override fun equals(other: Any?): Boolean =
            other is Recording && other.resourceName == resourceName && other.bytes.contentEquals(bytes)

        override fun hashCode(): Int = 31 * resourceName.hashCode() + bytes.contentHashCode()
    }
}
