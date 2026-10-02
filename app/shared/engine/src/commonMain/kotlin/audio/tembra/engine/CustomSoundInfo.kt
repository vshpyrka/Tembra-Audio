package audio.tembra.engine

/** What the engine made of an imported file: `tembra_custom_sound_info` (data-model.md §4). */
data class CustomSoundInfo(
    val sourceRate: Int,
    val sourceChannels: Int,
    /** 32 for float files. */
    val sourceBits: Int,
    /** After onset alignment and the 2 s cap, at the source rate. */
    val keptFrames: Int,
    val trimmedMs: Float,
    val keptMs: Float,
)
