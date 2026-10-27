package audio.tembra.engine

import audio.tembra.core.recording.TakeStopReason

/** The engine's `tembra_take_stop_reason` codes onto the core reasons; an unknown code reads as a write failure. */
internal object TakeStopReasonCodes {
    fun fromNative(code: Int): TakeStopReason = when (code) {
        0 -> TakeStopReason.User
        1 -> TakeStopReason.MaxLength
        2 -> TakeStopReason.DiskFull
        3 -> TakeStopReason.InputLost
        4 -> TakeStopReason.TransportClosed
        5 -> TakeStopReason.WriteFailed
        else -> TakeStopReason.WriteFailed
    }
}
