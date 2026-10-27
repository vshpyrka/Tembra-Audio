package audio.tembra.engine

/** Outcome of [TimelineAudio.arm] (contracts/engine-c-abi-v7.md "Arming"). */
sealed interface ArmResult {
    /** The input is open beside the output: [channels] (1 or 2) at [sampleRate], the stream's rate. */
    data class Armed(val channels: Int, val sampleRate: Int) : ArmResult

    /** The platform input could not open (`TEMBRA_ERR_DEVICE`): another app holds it, or the device has none. */
    data object InputUnavailable : ArmResult

    /** No transport is open (`TEMBRA_ERR_STATE`): arming needs a project on Timeline. */
    data object NotTransport : ArmResult
}
