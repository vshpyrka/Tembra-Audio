package audio.tembra.core.project

/** One file of an [ImportAsNewTracks]: the new track's id and name (the file name without extension) and its clip. */
data class NewTrackWithClip(val trackId: TrackId, val name: String, val clip: Clip)
