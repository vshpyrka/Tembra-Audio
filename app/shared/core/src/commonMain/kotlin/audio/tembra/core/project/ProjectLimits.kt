package audio.tembra.core.project

/** The engine's fixed arrangement bounds (contracts/engine-c-abi-v6.md) and the split margin of spec FR-032. */
object ProjectLimits {
    /** The arrangement snapshot holds at most this many tracks; the editor refuses the next one. */
    const val MAX_TRACKS = 64

    /** The arrangement snapshot holds at most this many clips. */
    const val MAX_CLIPS = 1024

    /** A split must lie strictly more than this far from both clip edges. */
    const val MIN_SPLIT_EDGE_SECONDS = 0.01
}
