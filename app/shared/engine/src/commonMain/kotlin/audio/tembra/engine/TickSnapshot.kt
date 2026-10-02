package audio.tembra.engine

/**
 * Mutable mirror of `tembra_metronome_tick`, one per reader, filled by [MetronomeAudio.readTick] without allocation
 * (data-model.md §6).
 */
class TickSnapshot {
    /** +1 per emitted tick since the start; 0 before the first. */
    var tickIndex: Long = 0L

    /** 0-based beat of the last emitted tick. */
    var beatIndex: Int = 0
    var level: ClickLevel = ClickLevel.Click

    /** When that tick reaches the listener, relative to the read (negative = already audible). */
    var audibleInNanos: Long = 0L
    var beatPeriodNanos: Long = 0L

    /** False when idle, and also after the platform closed the stream and it could not be reopened. */
    var running: Boolean = false
    var sampleRate: Int = 0

    fun copyFrom(other: TickSnapshot) {
        tickIndex = other.tickIndex
        beatIndex = other.beatIndex
        level = other.level
        audibleInNanos = other.audibleInNanos
        beatPeriodNanos = other.beatPeriodNanos
        running = other.running
        sampleRate = other.sampleRate
    }
}
