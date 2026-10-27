package audio.tembra.core.project

/**
 * One import, one undo step (spec FR-044): the accepted files land either on one existing track ([ImportToTrack]) or
 * each on a new track named after its file ([ImportAsNewTracks]). [assets] are the facts of the copies the import
 * made, merged into the document; each clip's `startBeats` is the requested position (the playhead) and the editor
 * moves it to the first free span at or after it.
 */
sealed interface ImportClips : ProjectEdit {
    val assets: Map<AudioAssetId, AudioAsset>
}
