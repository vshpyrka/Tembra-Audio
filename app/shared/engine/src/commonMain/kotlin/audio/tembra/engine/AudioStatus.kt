package audio.tembra.engine

import kotlinx.coroutines.flow.Flow

/** The two members every audio seam shares: what the engine is doing and what the platform reports (feature 004). */
interface AudioStatus {
    /** Idle, Listening, Tone or Metronome, read from the engine. */
    val audioState: AudioState

    /**
     * Platform interruptions and route changes (research R7). One collector is expected (the owning ViewModel) for as
     * long as audio can be running. The flow has no replay and no `.value`: an event emitted while nothing collects
     * is dropped, which is safe because a cleared ViewModel has already stopped every stream. A feature that must
     * see every interruption should move this to a buffered `Channel` exposed as `receiveAsFlow()`.
     */
    val events: Flow<AudioEvent>
}
