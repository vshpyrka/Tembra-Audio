package audio.tembra.ui.navigation

import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.metadata

/**
 * Navigation 3 metadata key under which an entry declares that its content fades itself in once it is ready
 * ([audio.tembra.ui.designsystem.PreparedLayout]); the navigation then fades only the screen it leaves.
 */
object FadesItselfInKey : NavMetadataKey<Boolean> {
    /** Metadata is stored under the key's string form; keep it stable rather than the object's identity hash. */
    override fun toString(): String = "FadesItselfInKey"
}

/** Entry metadata declaring that the entry fades its own content in. */
fun fadesItselfIn(): Map<String, Any> = metadata { put(FadesItselfInKey, true) }
