package audio.tembra.ui.tuner

import audio.tembra.ui.resources.Res
import org.jetbrains.compose.resources.MissingResourceException

/** The production [ResourceReader] over the Compose resources of this module; a missing file is a null, not an error. */
object ComposeResourceReader : ResourceReader {
    override suspend fun readOrNull(path: String): ByteArray? = try {
        Res.readBytes(path)
    } catch (e: MissingResourceException) {
        null
    }
}
