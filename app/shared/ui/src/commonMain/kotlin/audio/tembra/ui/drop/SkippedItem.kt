package audio.tembra.ui.drop

/** An item a drop leaves out and names in its message (feature 011, spec FR-026). */
data class SkippedItem(val name: String, val reason: SkipReason)

/** Why an item is left out; the importer's own refusals (unreadable or too long audio) join them after the import. */
enum class SkipReason {
    /** Not an audio file. */
    NotAudio,

    /** A folder the platform does not let the app read. */
    UnreadableFolder,

    /** A folder inside a dropped folder: a drop takes one level only. */
    NestedFolder,
}
