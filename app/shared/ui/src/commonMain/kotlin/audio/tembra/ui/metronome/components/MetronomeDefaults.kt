package audio.tembra.ui.metronome.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The metronome components' fixed metrics that are not theme tokens (research R8, R10, contracts/design-system-
 * additions.md): transcribed from the Stitch phone screen, kept in one place so no component hard-codes a value.
 */
internal object MetronomeDefaults {
    // Tempo dial (research R8): the SVG's strokes on its 240 viewBox, scaled with the dial.
    /** Track and fill stroke: 6 of 240. */
    val RingStroke: Dp = 6.dp

    /** The glow copy under the fill: 8 wide at 50 % (the SVG blur is replaced by a wider translucent stroke). */
    val RingGlowStroke: Dp = 8.dp
    const val RING_GLOW_ALPHA = 0.5f

    /** Tick marks: 1.5 wide in `outlineVariant` at 50 %. */
    val TickStroke: Dp = 1.5.dp
    const val TICK_ALPHA = 0.5f

    /** Stepper glyph: `text-[22px]`; steppers sit `px-2` inside the dial box. */
    val StepperIconSize: Dp = 22.dp
    val StepperInset: Dp = 8.dp

    /** A disabled stepper's glyph: Material's disabled content alpha. */
    const val DISABLED_ALPHA = 0.38f

    /** Press-and-hold on a stepper repeats after [HOLD_DELAY_MILLIS], then every [HOLD_INTERVAL_MILLIS]. */
    const val HOLD_DELAY_MILLIS = 400L
    const val HOLD_INTERVAL_MILLIS = 80L

    /** The page keys move the tempo by ten, the arrows and the wheel by one. */
    const val PAGE_STEP = 10

    // Accent cells (research R10).
    /** Gap between cells: `gap-2`. */
    val CellGap: Dp = 8.dp

    /** Cell padding `p-1.5` and the gap between the bar box and the label `gap-1.5`. */
    val CellPadding: Dp = 6.dp

    /** Bar box padding: `p-1`. */
    val BarBoxPadding: Dp = 4.dp

    /** Bar box corners `rounded-md` and bar corners `rounded-sm`. */
    val BarBoxRadius: Dp = 6.dp
    val BarRadius: Dp = 2.dp

    /** Bar heights by level: `h-[95%]`, `h-[62%]`, `h-[42%]`. */
    const val ACCENT_BAR_FRACTION = 0.95f
    const val CLICK_BAR_FRACTION = 0.62f
    const val SUB_BAR_FRACTION = 0.42f

    /** The Sub bar is `primary/70`; a lit bar is drawn at full alpha (research R7). */
    const val SUB_BAR_ALPHA = 0.7f

    /** The lit cell's bar-box outline in `secondary`. */
    val LitOutlineWidth: Dp = 2.dp

    /** A lit Accent bar's glow is this much stronger (off under reduce motion). */
    const val GLOW_LIT_BOOST = 1.6f

    /** The glow pulse while running: its alpha breathes between this share and full over [GLOW_PULSE_MILLIS]. */
    const val GLOW_PULSE_MIN = 0.6f
    const val GLOW_PULSE_MILLIS = 1200

    // Play button and tap tempo (research R10).
    /** Play and pause glyph: `text-[34px]`, filled. */
    val PlayIconSize: Dp = 34.dp

    /** Tap tempo glyph: `text-[20px]`. */
    val TapIconSize: Dp = 20.dp

    // Slider (research R10): the 6 dp track and the 16 dp thumb.
    val SliderTrackHeight: Dp = 6.dp
    val SliderThumbSize: Dp = 16.dp
}
