package audio.tembra.core.project

/**
 * A finished take placed on its track as one edit (feature 010, spec FR-011 to FR-013; data-model.md section 1): the
 * take's audio copy ([assetId], [asset]) and its clip ([clip]: offset 0, length = the asset's frames, the name chosen
 * with `Track.nextTakeName()` at record start) under the fresh [clipId] at [startBeats]. The ids travel in the edit
 * so applying it is pure and replayable, like `SplitClip`'s. The editor derives the id of any right-hand part it has
 * to make from a clip straddling both ends of the take (`<clipId>-after-<that clip's id>`) from [clipId], so no
 * further id is needed.
 */
data class RecordTake(
    val trackId: TrackId,
    val assetId: AudioAssetId,
    val asset: AudioAsset,
    val clipId: ClipId,
    val clip: ClipTemplate,
    val startBeats: Beats,
) : ProjectEdit
