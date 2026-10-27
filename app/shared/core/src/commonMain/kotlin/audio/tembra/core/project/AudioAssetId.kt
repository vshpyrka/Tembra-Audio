package audio.tembra.core.project

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

/**
 * The content identifier of an audio copy: the lower-case SHA-256 hex of the file bytes (spec FR-049). The same file
 * imported twice is one asset; the id is also the stored file's base name (`audio/<sha256>.<ext>`).
 */
@JvmInline
@Serializable
value class AudioAssetId(val sha256: String)
