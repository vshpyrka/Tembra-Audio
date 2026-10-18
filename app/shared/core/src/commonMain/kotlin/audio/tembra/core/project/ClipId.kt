package audio.tembra.core.project

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

/** The stable identity of a clip: a UUID string from [IdGenerator]; a split gives both halves new ids. */
@JvmInline
@Serializable
value class ClipId(val value: String)
