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

    // --- Capture, takes and monitoring (feature 010, contracts/engine-c-abi-v7.md "Kotlin seam") ---------------------

    /**
     * Opens the input beside the transport's output with the device's channels up to [maxChannels] (IO dispatcher:
     * Android opens the second stream, iOS switches the session and restarts the unit). Idempotent while armed.
     */
    suspend fun arm(maxChannels: Int): ArmResult

    /** Ends a running take (kept), turns monitoring off and closes the input; idempotent. */
    fun disarm()

    /** Monitoring of the armed track [trackIndex]'s input through its strip; the engine clears it on an output reopen. */
    fun setMonitor(enabled: Boolean, trackIndex: Int)

    /** Starts a take (IO dispatcher: the staging file is created and the position primed); see [RecordConfig]. */
    suspend fun recordStart(config: RecordConfig): RecordStartResult

    /** Pre-roll only: pauses, restores the position and deletes the staging file. */
    fun recordCancel()

    /**
     * Ends capture at the next block, drains the recorder and finalises the file (IO dispatcher, at most 1 s); also
     * collects the info of a take the engine ended itself. Playback continues.
     */
    suspend fun recordStop(): TakeInfo

    /** Lock-free; safe every frame; main thread only (one scratch per engine). Zeros when nothing is armed. */
    fun readCapture(into: CaptureSnapshot)

    /** Copies the live peak buckets from [fromIndex] into [into]; returns the next index to ask for. Main thread. */
    fun readPeaks(fromIndex: Long, into: PeakBuckets): Long

    /** Clears the meter's clip latch. */
    fun resetClip()

    /** Free bytes on the volume holding [path]; -1 when unknown. */
    fun freeBytes(path: String): Long
}
