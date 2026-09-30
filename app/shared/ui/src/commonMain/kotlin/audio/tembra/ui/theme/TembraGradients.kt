package audio.tembra.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The tuner's and the metronome's gradients and glows, transcribed from the Stitch HTML (feature 003 data-model.md §8,
 * research R9; feature 004 data-model.md §9, research R10). Every stop is a scheme colour with alpha except the
 * documented in-gradient stops `#D946EF` ([meterCompact]) and `#1E1B2E` ([tempoDialCard]).
 */
@Immutable
data class TembraGradients(
    /** Meter card: `radial-gradient(circle at 60% 45%, #A78BFA 12%, #D946EF 6% at 35%, transparent at 70%)`. */
    val meterCompact: RadialGlow,
    /** By Ear card on compact windows: `circle at 50% 30%, #AB88FF 6%, #85145A 6% at 40%, transparent at 75%`. */
    val byEarCompact: RadialGlow,
    /** By Ear card on expanded windows: `circle at 60% 50%, #A78BFA 10%, #85145A 8% at 45%, transparent at 75%`. */
    val byEarWide: RadialGlow,
    /** The `primary-container/10` blurred disc: outside the top-start corner, or bottom-start under `CardDiscs.TopEndBottomStart`. */
    val discTopStart: Color,
    /** The `secondary-container/15` blurred disc: outside the bottom-end corner, or top-end under `CardDiscs.TopEndBottomStart`. */
    val discBottomEnd: Color,
    /** Disc radius (`w-56` = 224 px diameter) and how far its centre sits outside the card corner (`-top-16`). */
    val discRadius: Dp = 112.dp,
    val discOffset: Dp = 64.dp,
    /** Needle glow: `rgba(255,175,211,0.7)` 8 px. */
    val needleGlow: Glow,
    /** Playing card glow: `shadow-[0_0_18px_rgba(133,20,90,0.4)]`, drawn not shadowed. */
    val playingGlow: Glow,
    /** Tempo ring: `primary` → `primaryContainer` at 50 % → `secondary` across the dial box. */
    val tempoArc: LinearGlow,
    /** Dial card: `radial-gradient(circle, rgba(167,139,250,0.08) 0%, rgba(30,27,46,0.4) 40%, rgba(19,19,22,0.95) 100%)`. */
    val tempoDialCard: RadialGlow,
    /** Dial card overlay: `from-primary/10 via-transparent to-secondary/10`, top to bottom. */
    val tempoDialOverlay: LinearGlow,
    /** Accent bar glow: `0 0 12px rgba(255,175,211,0.5)`, drawn not shadowed. */
    val accentGlow: Glow,
    /** Play button glow: `0 0 24px rgba(255,175,211,0.35)`, drawn not shadowed. */
    val playGlow: Glow,
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
            tempoArc = LinearGlow(
                start = Offset(0f, 0f),
                end = Offset(1f, 1f),
                stops = listOf(0f to Color(0xFFCEBDFF), 0.5f to Color(0xFFA78BFA), 1f to Color(0xFFFFAFD3)),
            ),
            tempoDialCard = RadialGlow(
                center = Offset(0.5f, 0.5f),
                stops = listOf(0f to Color(0x14A78BFA), 0.4f to Color(0x661E1B2E), 1f to Color(0xF2131316)),
            ),
            tempoDialOverlay = LinearGlow(
                start = Offset(0.5f, 0f),
                end = Offset(0.5f, 1f),
                stops = listOf(0f to Color(0x1ACEBDFF), 0.5f to Color.Transparent, 1f to Color(0x1AFFAFD3)),
            ),
            accentGlow = Glow(Color(0x80FFAFD3), 12.dp),
            playGlow = Glow(Color(0x59FFAFD3), 24.dp),
        )
    }
}
