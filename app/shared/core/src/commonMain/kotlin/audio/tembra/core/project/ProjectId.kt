package audio.tembra.core.project

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

/** The stable identity of a project: a UUID string from [IdGenerator], serialised as the bare string (`kotlin-api-design`: a single-field domain distinction). */
@JvmInline
@Serializable
value class ProjectId(val value: String)
