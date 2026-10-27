package audio.tembra.engine

import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.core.project.Beats

/**
 * The transport and timeline half of the engine (contracts/timeline-audio-kotlin.md, ABI revision 6): the seam the
 * project session programs against. Main-thread entry points; the suspend members hop to the IO dispatcher inside
 * the actuals (the prime before play, the MP3 seek table of a clip source, the offline inspector and overview
 * builder) and return on the caller's dispatcher. Nothing here ever runs on the audio callback.
 */
interface TimelineAudio : AudioStatus {
    /** Opens the output stream for a project: Idle → Transport, position 0, stopped. */
    suspend fun openTransport(): TransportOpenResult

    /** Pauses with the release, joins the streamer, closes the stream → Idle; idempotent. */
    fun closeTransport()

    /** Opens a decoder on the app's copy of an asset; allowed while Idle and Transport. */
    suspend fun openClipSource(path: String): ClipSourceResult

    /** Refused silently while an arrangement references [handle]: the session removes it from the arrangement first. */
    fun closeClipSource(handle: ClipSourceHandle)

    /** Applies at the next block and re-anchors the click. */
    fun setTempo(tempo: Tempo, signature: TimeSignature)

    /** Validated and copied into the engine's triple buffer; applies at the next block. */
    fun setArrangement(snapshot: ArrangementSnapshot)

    /** The fast path for one track's gain target and audibility, without a new arrangement. */
    fun setTrack(index: Int, gainLinear: Float, audible: Boolean)

    /** 0..1, smoothed over 10 ms. */
    fun setMaster(gainLinear: Float)

    /** The anchored click joins the mix at the next beat; off releases its voice. The grid goes through [MetronomeAudio.setGrid]. */
    fun setMetronome(enabled: Boolean)

    /** Primes every clip within two seconds of the position (under 50 ms), then starts. */
    suspend fun play(): PlayResult

    /** The 5 ms release, position kept; idempotent while stopped. */
    fun pause()

    /** Any transport state; while playing a silent re-prime and resume from the new position within 100 ms. */
    suspend fun seek(beats: Beats)

    /**
     * Lock-free; safe to call every frame from draw lambdas. Fills [into] without allocating. Main thread only: the
     * facades reuse one scratch buffer per engine, so two threads reading at once would tear a snapshot.
     */
    fun readTransport(into: TransportSnapshot)

    /** Offline: opens and closes a decoder on [path]; null for anything the engine cannot read. */
    suspend fun inspect(path: String): AudioInfo?

    /** Offline and long: decodes [audioPath] once and writes the `.peaks` overview to [overviewPath]; false on failure. */
    suspend fun buildOverview(audioPath: String, overviewPath: String): Boolean
}
