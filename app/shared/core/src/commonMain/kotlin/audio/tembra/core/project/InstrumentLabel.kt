package audio.tembra.core.project

/**
 * Exactly the twelve instrument labels of spec FR-021, in display order. The document stores the lower-case name; an
 * unknown name reads as [Other] (contracts/project-document.md, evolution rule 7). The glyph each label carries is the
 * UI's mapping.
 */
enum class InstrumentLabel(val displayName: String) {
    Vocals("Vocals"),
    Guitar("Guitar"),
    Bass("Bass"),
    Drums("Drums"),
    Percussion("Percussion"),
    Keys("Keys"),
    Synth("Synth"),
    Strings("Strings"),
    Brass("Brass"),
    Woodwind("Woodwind"),
    Loops("Loops"),
    Other("Other");

    /** The document's spelling: the lower-case enum name. */
    val serialName: String get() = name.lowercase()

    companion object {
        /** The label with this serial name; [Other] for anything this reader does not know. */
        fun fromSerialName(name: String): InstrumentLabel = entries.firstOrNull { it.serialName == name } ?: Other
    }
}
