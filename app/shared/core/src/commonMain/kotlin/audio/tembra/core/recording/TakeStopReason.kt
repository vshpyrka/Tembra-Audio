package audio.tembra.core.recording

/**
 * Why a take ended (feature 010, data-model.md §2; spec FR-021, FR-025): the user, the engine's own limits, the
 * platform taking the audio or the input, the permission, the transport closing, a write failure, or leaving
 * Timeline. The engine's `tembra_take_stop_reason` maps onto the first six in the engine module; the last three are
 * host-side reasons. One closed type, so every `when` over it is exhaustive (`kotlin-control-flow`).
 */
enum class TakeStopReason {
    User,
    MaxLength,
    DiskFull,
    InputLost,
    Interrupted,
    PermissionRevoked,
    TransportClosed,
    WriteFailed,
    Leaving,
}
