package audio.tembra.core.project

import kotlin.uuid.Uuid

/**
 * Fresh identities for projects, tracks and clips: random UUID v4 strings (data-model.md section 1). Edits that create
 * entities carry their ids as constructor defaults taken from here, so `ProjectEditor.apply` itself stays pure.
 */
object IdGenerator {
    fun projectId(): ProjectId = ProjectId(uuid())

    fun trackId(): TrackId = TrackId(uuid())

    fun clipId(): ClipId = ClipId(uuid())

    private fun uuid(): String = Uuid.random().toString()
}
