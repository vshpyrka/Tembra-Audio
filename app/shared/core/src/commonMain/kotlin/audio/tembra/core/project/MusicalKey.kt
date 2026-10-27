package audio.tembra.core.project

/**
 * The 24 keys a project may declare (spec FR-003, FR-052): twelve major then twelve minor, sharps only, with the
 * design's display names ("Cmaj", "F#maj", "Dmin"). The document stores the display name; an unknown name reads as no
 * key (contracts/project-document.md, evolution rule 7).
 */
enum class MusicalKey(val displayName: String) {
    CMajor("Cmaj"),
    CSharpMajor("C#maj"),
    DMajor("Dmaj"),
    DSharpMajor("D#maj"),
    EMajor("Emaj"),
    FMajor("Fmaj"),
    FSharpMajor("F#maj"),
    GMajor("Gmaj"),
    GSharpMajor("G#maj"),
    AMajor("Amaj"),
    ASharpMajor("A#maj"),
    BMajor("Bmaj"),
    CMinor("Cmin"),
    CSharpMinor("C#min"),
    DMinor("Dmin"),
    DSharpMinor("D#min"),
    EMinor("Emin"),
    FMinor("Fmin"),
    FSharpMinor("F#min"),
    GMinor("Gmin"),
    GSharpMinor("G#min"),
    AMinor("Amin"),
    ASharpMinor("A#min"),
    BMinor("Bmin");

    companion object {
        /** The key with this display name, or null for anything else. */
        fun fromDisplayName(name: String): MusicalKey? = entries.firstOrNull { it.displayName == name }
    }
}
