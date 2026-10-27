package audio.tembra.core.project

/** Removes a clip (spec FR-034); an asset no clip uses any more leaves the document (its file goes once the history lets it). */
data class DeleteClip(val clipId: ClipId) : ProjectEdit
