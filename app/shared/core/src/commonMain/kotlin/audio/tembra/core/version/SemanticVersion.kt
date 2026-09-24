package audio.tembra.core.version

/**
 * A `major.minor.patch` version with non-negative components, ordered numerically.
 * Used for the engine descriptor now and for the project file schema version later.
 */
data class SemanticVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<SemanticVersion> {

    init {
        require(major >= 0 && minor >= 0 && patch >= 0) { "Version components must be non-negative: $major.$minor.$patch" }
    }

    override fun compareTo(other: SemanticVersion): Int =
        compareValuesBy(this, other, SemanticVersion::major, SemanticVersion::minor, SemanticVersion::patch)

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val PATTERN = Regex("""(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)""")

        /** Parses exactly three dot-separated non-negative integers; throws [IllegalArgumentException] otherwise. */
        fun parse(text: String): SemanticVersion {
            val match = PATTERN.matchEntire(text)
                ?: throw IllegalArgumentException("Not a semantic version (expected major.minor.patch): \"$text\"")
            val (major, minor, patch) = match.destructured
            return SemanticVersion(major.toInt(), minor.toInt(), patch.toInt())
        }

        fun parseOrNull(text: String): SemanticVersion? = PATTERN.matchEntire(text)?.let { parse(text) }
    }
}
