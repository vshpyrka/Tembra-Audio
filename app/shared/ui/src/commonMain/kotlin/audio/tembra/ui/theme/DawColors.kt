package audio.tembra.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** DAW-specific colours from design/stitch/theme.json → dawSemanticColors; not part of the Material scheme. */
@Immutable
data class DawColors(
    val canvasLevel0: Color,
    val surfaceLevel1: Color,
    val surfaceLevel2: Color,
    val surfaceLevel2Border: Color,
    val surfaceLevel3: Color,
    val surfaceLevel4: Color,
    val lavenderPrimary: Color,
    val lavenderLight: Color,
    val playhead: Color,
    val rose: Color,
    val roseLight: Color,
    val magenta: Color,
    val winePad: Color,
    val winePadMid: Color,
    val winePadActive: Color,
    val gridOverlayFaint: Color,
    val gridOverlay: Color,
    /** `clip_glow = 0 0 12px rgba(167, 139, 250, 0.25)`: the only glow in the design, drawn, never a shadow. */
    val clipGlow: Color,
    val clipGlowBlur: Dp,
) {
    companion object {
        val Default = DawColors(
            canvasLevel0 = Color(0xFF0F0F12),
            surfaceLevel1 = Color(0xFF18171C),
            surfaceLevel2 = Color(0xFF212026),
            surfaceLevel2Border = Color(0xFF2B2A34),
            surfaceLevel3 = Color(0xFF292832),
            surfaceLevel4 = Color(0xFF32303C),
            lavenderPrimary = Color(0xFFA78BFA),
            lavenderLight = Color(0xFFC4B5FD),
            playhead = Color(0xFFC084FC),
            rose = Color(0xFFF472B6),
            roseLight = Color(0xFFFDA4AF),
            magenta = Color(0xFFE879F9),
            winePad = Color(0xFF5B2A3D),
            winePadMid = Color(0xFF78354E),
            winePadActive = Color(0xFF9D4B68),
            gridOverlayFaint = Color(0x1FA855F7),
            gridOverlay = Color(0x33C084FC),
            clipGlow = Color(0x40A78BFA),
            clipGlowBlur = 12.dp,
        )
    }
}
