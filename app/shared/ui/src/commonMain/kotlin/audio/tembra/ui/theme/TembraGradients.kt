package audio.tembra.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The tuner screens' gradients and glows, transcribed from the Stitch HTML (data-model.md §8, research R9). */
@Immutable
data class TembraGradients(
    /** Meter card: `radial-gradient(circle at 60% 45%, #A78BFA 12%, #D946EF 6% at 35%, transparent at 70%)`. */
    val meterCompact: RadialGlow,
    /** By Ear card on compact windows: `circle at 50% 30%, #AB88FF 6%, #85145A 6% at 40%, transparent at 75%`. */
    val byEarCompact: RadialGlow,
    /** By Ear card on expanded windows: `circle at 60% 50%, #A78BFA 10%, #85145A 8% at 45%, transparent at 75%`. */
    val byEarWide: RadialGlow,
    /** The blurred disc outside the top-start corner: `primary-container/10`. */
    val discTopStart: Color,
    /** The blurred disc outside the bottom-end corner: `secondary-container/15`. */
    val discBottomEnd: Color,
    /** Disc radius (`w-56` = 224 px diameter) and how far its centre sits outside the card corner (`-top-16`). */
    val discRadius: Dp = 112.dp,
    val discOffset: Dp = 64.dp,
    /** Needle glow: `rgba(255,175,211,0.7)` 8 px. */
    val needleGlow: Glow,
    /** Playing card glow: `shadow-[0_0_18px_rgba(133,20,90,0.4)]`, drawn not shadowed. */
    val playingGlow: Glow,
) {
    companion object {
        val Default = TembraGradients(
            meterCompact = RadialGlow(
                center = Offset(0.6f, 0.45f),
                stops = listOf(0f to Color(0x1FA78BFA), 0.35f to Color(0x0FD946EF), 0.7f to Color.Transparent),
            ),
            byEarCompact = RadialGlow(
                center = Offset(0.5f, 0.3f),
                stops = listOf(0f to Color(0x0FAB88FF), 0.4f to Color(0x0F85145A), 0.75f to Color.Transparent),
            ),
            byEarWide = RadialGlow(
                center = Offset(0.6f, 0.5f),
                stops = listOf(0f to Color(0x1AA78BFA), 0.45f to Color(0x1485145A), 0.75f to Color.Transparent),
            ),
            discTopStart = Color(0x1AA78BFA),
            discBottomEnd = Color(0x2685145A),
            needleGlow = Glow(Color(0xB3FFAFD3), 8.dp),
            playingGlow = Glow(Color(0x6685145A), 18.dp),
        )
    }
}
