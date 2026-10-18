package audio.tembra.core.project

/** "Add audio…" onto the selected track (spec FR-044): the clips are placed one after another from their requested start. */
data class ImportToTrack(
    val trackId: TrackId,
    val clips: List<Clip>,
    override val assets: Map<AudioAssetId, AudioAsset> = emptyMap(),
) : ImportClips
