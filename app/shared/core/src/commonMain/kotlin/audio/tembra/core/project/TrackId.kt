package audio.tembra.core.project

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

/** The stable identity of a track for the life of the project: a UUID string from [IdGenerator]. */
@JvmInline
@Serializable
value class TrackId(val value: String)
