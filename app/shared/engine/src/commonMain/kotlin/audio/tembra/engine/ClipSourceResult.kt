package audio.tembra.engine

/** Outcome of [TimelineAudio.openClipSource]. */
sealed interface ClipSourceResult {
    /** The decoder is open; [info] is what it reports (channels, rate, frames, format). */
    data class Opened(val handle: ClipSourceHandle, val info: AudioInfo) : ClipSourceResult

    /** The file is missing or no decoder reads it: the clip is unavailable (spec FR-030). */
    data object Undecodable : ClipSourceResult
}
