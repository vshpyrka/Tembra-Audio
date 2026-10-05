package audio.tembra.ui.metronome.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

    /** The smallest "120" may be with "BPM" beside it; below that "BPM" sets under the number. */
    val ReadoutNumberFloor: TextUnit = 28.sp

    /**
     * The smallest the readout's marking steps down to before it would clip. 6 sp, under the 8 sp of the cell labels,
     * because the case that reaches it is the largest font: at font scale 2 a phone held sideways (892 × 330 dp) gets a
     * 188 dp ring whose readout is 68 dp wide, and "Moderato" at an 8 sp floor needs about 70 dp there. 6 sp at font
     * scale 2 draws 12 dp tall, larger than 8 sp at the default scale; at scale 1 even a 160 dp ring's 40 dp readout fits
     * "Moderato" above 8 sp, so the lower floor only takes effect at large fonts.
     */
    val MarkingMinSize: TextUnit = 6.sp

    /** The smallest the tap card's title and caption step down to, in the landscape block's 48–64 dp row only. */
    val TapTextMinSize: TextUnit = 10.sp

    /** The page keys move the tempo by ten, the arrows and the wheel by one. */
    const val PAGE_STEP = 10

    // Accent cells (research R10).
    /** The smallest a cell's level label shrinks to rather than clip. */
    val CellLabelMinSize: TextUnit = 7.sp

    /** Gap between cells: `gap-2` (the compact bar-shape card uses `TembraSpacing.xs` so 7/8 keeps one row). */
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

    // Style row (research R10).
    /** Gap between the style pills: `gap-1.5`. */
    val PillGap: Dp = 6.dp

    // Settings sections (research R10): `space-y-1.5` between a section's header and its chips.
    val SectionGap: Dp = 6.dp

    /** Between the beat cells and the style row: `space-y-2`. */
    val CellsStyleGap: Dp = 8.dp

    // Play button and tap tempo (research R10).
    /** Play and pause glyph: `text-[34px]`, filled. */
    val PlayIconSize: Dp = 34.dp

    /** Tap tempo glyph: `text-[20px]`. */
    val TapIconSize: Dp = 20.dp

    /** The per-tap flash: `bg-primary/20` over the card, fading out over [TAP_FLASH_MILLIS]. */
    const val TAP_FLASH_ALPHA = 0.2f
    const val TAP_FLASH_MILLIS = 120

    // Click sound picker (research R10).
    /** The pill's waveform glyph `text-[18px]`, its chevron `text-[16px]` and its padding `px-3 py-1.5`. */
    val SoundPillIconSize: Dp = 18.dp
    val SoundPillChevronSize: Dp = 16.dp
    val SoundPillVerticalPadding: Dp = 6.dp
    /**
     * Upper bound for a dropdown menu's height, seven entries plus the menu padding. Compose's iOS popup layer can
     * hand the menu an unbounded height after orientation changes, and the menu's scrolling column then throws
     * "Vertically scrollable component was measured with an infinity maximum height" (crash seen on the iPhone 17
     * simulator, 2026-10-01); a bounded maximum keeps the column measurable whatever the layer offers.
     */
    val MenuMaxHeight: Dp = 360.dp


    /** The menu's check and a tile's glyph: `text-[16px]`. */
    val SoundGlyphSize: Dp = 16.dp

    /** The tiles' grid gap `gap-1.5` and a tile's padding `p-2.5`. */
    val SoundTileGap: Dp = 6.dp
    val SoundTilePadding: Dp = 10.dp

    /** Tints at 20 % (`/20`): the selected tile's fill, the empty Custom glyph box and an unselected tile's border. */
    const val SOUND_TINT_ALPHA = 0.2f

    /** The empty Custom tile's dashed border: `border-outline-variant/30`, dashes of 4 on 4. */
    const val DASHED_BORDER_ALPHA = 0.3f
    val DashLength: Dp = 4.dp

    // Wide and landscape layouts (contracts/metronome-screen.md, research R10).
    /** The expanded layout's tap and play row: `max-w-md` centred in its card. */
    val TransportMaxWidth: Dp = 448.dp

    /** The expanded layout's settings column: a third of the width (at least `TembraSpacing.wideSettingsMinWidth`). */
    const val WIDE_SETTINGS_FRACTION = 1f / 3f

    /**
     * The landscape settings column (T150, T152): 2⁄5 of the width (a 2:3 split against the dial pane, the user's
     * choice), at least [LandscapeSettingsMinWidth] scaled by the font scale; it scrolls only when the card is taller
     * than the screen.
     */
    const val LANDSCAPE_SETTINGS_FRACTION = 2f / 5f
    val LandscapeSettingsMinWidth: Dp = 240.dp

    /**
     * The landscape block's tap and play row (T152): [LandscapeRowMin] for panes up to [LandscapeRowScaleFrom] tall,
     * growing linearly to [LandscapeRowMax] at [LandscapeRowScaleTo]; the targets never go under 44 dp.
     */
    val LandscapeRowMin: Dp = 48.dp
    val LandscapeRowMax: Dp = 64.dp
    val LandscapeRowScaleFrom: Dp = 300.dp
    val LandscapeRowScaleTo: Dp = 420.dp

    /** The dial grows with the space it is given down to this; below it the column or the pane scrolls instead. */
    val DialMin: Dp = 160.dp

    /** The compact layout's dial grows with the height left under the settings up to this (design iteration D1). */
    val CompactDialMax: Dp = 300.dp

    /**
     * Above a tabletop fold (feature 005): the accent cells' bar box grows with the region's height from
     * `TembraSpacing.accentCellBarHeight` up to [TabletopCellBarMax], leaving [TabletopCellLabelRoom] for the level label.
     */
    val TabletopCellBarMax: Dp = 160.dp

    /**
     * Above a tabletop fold the dial card and the accent card sit side by side from this width (a book-style
     * foldable's inner screen) and stack under it (a flip-style foldable).
     */
    val TabletopSideBySideMinWidth: Dp = 600.dp
    val TabletopCellLabelRoom: Dp = 28.dp

    /** A subdivision tile's note figure inside its 44 dp tile (design iteration D1). */
    val SubdivisionGlyphSize: Dp = 24.dp
}
