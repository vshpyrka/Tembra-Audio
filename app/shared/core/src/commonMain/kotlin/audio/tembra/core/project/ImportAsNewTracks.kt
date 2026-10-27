package audio.tembra.core.project

/** "Add audio as new track" (spec FR-044): one new track per accepted file, in the picker's order, as one edit. */
data class ImportAsNewTracks(
    val tracks: List<NewTrackWithClip>,
    override val assets: Map<AudioAssetId, AudioAsset> = emptyMap(),
) : ImportClips
