package audio.tembra.ui.mixer

/** What a strip's meter reads (feature 012, spec FR-014): the track's playback level, or the microphone while the track is armed. */
enum class MeterSource {
    Playback,
    Input,
}
