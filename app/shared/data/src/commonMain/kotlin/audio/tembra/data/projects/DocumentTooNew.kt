package audio.tembra.data.projects

/** A document whose `formatVersion` is above this reader's (contracts/project-document.md rule 2): refused, never round-tripped blindly. */
data class DocumentTooNew(val version: Int) : DecodeResult
