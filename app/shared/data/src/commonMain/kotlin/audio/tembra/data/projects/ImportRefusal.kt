package audio.tembra.data.projects

/** Why an import was refused (spec FR-044): the message names the reason. */
enum class ImportRefusal {
    UnsupportedFormat,
    TooManyChannels,
    TooLong,
    TooLarge,
    Unreadable,
}
