package audio.tembra.engine

/**
 * One clip as the engine schedules it (`tembra_clip_state`): [startBeats] is converted to frames by the engine's tempo
 * map at render time; [offsetFrames] and [lengthFrames] are at the source's own rate (data-model.md §3).
 */
data class ClipRender(
    val trackIndex: Int,
    val source: ClipSourceHandle,
    val startBeats: Double,
    val offsetFrames: Long,
    val lengthFrames: Long,
    val sourceRate: Int,
)
