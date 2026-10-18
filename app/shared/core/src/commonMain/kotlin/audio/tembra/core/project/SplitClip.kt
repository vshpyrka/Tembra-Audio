package audio.tembra.core.project

/**
 * The scissors (spec FR-032): splits a clip at [atBeats] into two clips that together play exactly as the original;
 * both keep the name and get the fresh ids [leftId] and [rightId].
 */
data class SplitClip(
    val clipId: ClipId,
    val atBeats: Beats,
    val leftId: ClipId = IdGenerator.clipId(),
    val rightId: ClipId = IdGenerator.clipId(),
) : ProjectEdit
