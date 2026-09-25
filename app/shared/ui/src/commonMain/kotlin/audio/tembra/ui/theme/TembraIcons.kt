package audio.tembra.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.vectorResource

/** Resolves a [TembraIcon] to a tintable vector; the same bytes render on both platforms (spec SC-006). */
object TembraIcons {
    /** The glyph as an ImageVector, tintable through `Icon(tint = ...)`. */
    @Composable
    fun vector(icon: TembraIcon): ImageVector = vectorResource(icon.resource)
}
