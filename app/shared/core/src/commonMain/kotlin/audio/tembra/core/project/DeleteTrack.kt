package audio.tembra.core.project

/** Removes a track and its clips (spec FR-026); its solo state goes with it and assets no clip uses leave the document. */
data class DeleteTrack(val id: TrackId) : ProjectEdit
