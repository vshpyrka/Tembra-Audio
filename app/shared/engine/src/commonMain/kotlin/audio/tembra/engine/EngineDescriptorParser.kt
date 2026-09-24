package audio.tembra.engine

import audio.tembra.core.version.SemanticVersion

/** Parses the C ABI describe string `"<name> <version> (<lib> <ver>; <lib> <ver>; ...)"`. */
internal object EngineDescriptorParser {
    private val PATTERN = Regex("""^(.+?) (\d+\.\d+\.\d+) \((.*)\)$""")

    fun parse(raw: String): EngineDescriptor {
        val match = PATTERN.matchEntire(raw)
            ?: throw IllegalArgumentException("Malformed engine description: \"$raw\"")
        val (name, version, libraries) = match.destructured
        return EngineDescriptor(
            name = name,
            version = SemanticVersion.parse(version),
            compiledLibraries = libraries.split(";").map { it.trim() }.filter { it.isNotEmpty() },
            rawDescription = raw,
        )
    }
}
