package audio.tembra.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spacing scale from design/stitch/theme.json → spacing and touchTargetMin; `seam` is the 1 px border of DESIGN.md. */
@Immutable
data class TembraSpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 20.dp,
    val xl: Dp = 28.dp,
    val gutter: Dp = 12.dp,
    val pageMargin: Dp = 16.dp,
    val touchTargetMin: Dp = 44.dp,
    val seam: Dp = 1.dp,
    /** Reading-width cap for centred content on wide windows (plan: placeholder wide layout). */
    val wideContentMaxWidth: Dp = 720.dp,
    // Chrome metrics of the navigation frame (feature 002, data-model.md §6, spec FR-019).
    /** [audio.tembra.ui.designsystem.TembraTopBar]'s content height (`h-14` on the wide design screens); the shell shows no top bar since 2026-10-16. */
    val topBarHeight: Dp = 56.dp,
    /** Bottom bar content height: `h-16` on every phone bottom bar (the system inset is added below it). */
    val bottomBarHeight: Dp = 64.dp,
    /** Navigation rail width: `w-16`. */
    val railWidth: Dp = 64.dp,
    /** Supporting pane width: `w-80` inspector; DESIGN.md desktop layout. */
    val supportingPaneWidth: Dp = 320.dp,
    /** Bottom-bar item minimum width: `min-w-[56px]`. */
    val navItemMinWidth: Dp = 56.dp,
    /** Rail item width: rail `px-2` inside 64 dp. */
    val railItemWidth: Dp = 48.dp,
    /** Bottom-bar glyph size: `text-[20px]`. */
    val navBarIconSize: Dp = 20.dp,
    /** Rail glyph size: `text-[22px]`. */
    val navRailIconSize: Dp = 22.dp,
    /** Gap between a navigation glyph and its label: `gap-1`. */
    val navItemGap: Dp = 4.dp,
    /** Keyboard focus ring drawn in `primary` around a focused control (Constitution IV, spec FR-014). */
    val focusRingWidth: Dp = 2.dp,
    // Tuner metrics from the Stitch tuner screens (feature 003, data-model.md §8, research R9).
    /** Instrument segment height: `h-8`. */
    val segmentHeightLarge: Dp = 32.dp,
    /** String-count and mode segment height: `h-7`. */
    val segmentHeightSmall: Dp = 28.dp,
    /** Meter width on compact and medium windows: `max-w-[340px]`. */
    val meterMaxWidth: Dp = 340.dp,
    /** Meter width on expanded windows (the tablet screen's centred gauge). */
    val meterMaxWidthWide: Dp = 480.dp,
    /** By Ear grid width on compact and medium windows: `max-w-sm`. */
    val byEarGridMaxWidth: Dp = 384.dp,
    /** By Ear grid width on expanded windows: `max-w-xl`. */
    val byEarGridMaxWidthWide: Dp = 576.dp,
    /** The smallest a wide By Ear card shrinks to fit the height: its numeral, note and Audition button still fit. */
    val stringCardMinWide: Dp = 128.dp,
    /** Loop toggle track: `w-8 h-4`, knob `w-3 h-3`. */
    val toggleWidth: Dp = 32.dp,
    val toggleHeight: Dp = 16.dp,
    val toggleKnob: Dp = 12.dp,
    /** Status dot of the audio-state and in-tune pills: `w-1.5 h-1.5`. */
    val statusDot: Dp = 6.dp,
    /** Large segment glyph: `w-4 h-4`. */
    val segmentIconLarge: Dp = 16.dp,
    /** Small segment, pill and card-button glyph: `text-[14px]`. */
    val segmentIconSmall: Dp = 14.dp,
    /** Preset picker glyphs: `text-[18px]`. */
    val pickerIconSize: Dp = 18.dp,
    /** Flat and Sharp arrows on the meter: `text-[20px]`. */
    val meterArrowSize: Dp = 20.dp,
    /** The denied state's microphone glyph. */
    val deniedIconSize: Dp = 40.dp,
    /** Reading-width cap of the denied state's text. */
    val deniedTextMaxWidth: Dp = 360.dp,
    /** Visual height of a By Ear card button: `py-1.5` around a 14 px glyph. */
    val cardButtonHeight: Dp = 36.dp,
    /** Narrowest useful preset picker: the overline plus a preset name before the toggle wraps below it. */
    val presetPickerMinWidth: Dp = 200.dp,
    /** Large segment horizontal padding: `px-space-md`; small: `px-space-sm`. */
    val segmentPaddingLarge: Dp = 12.dp,
    val segmentPaddingSmall: Dp = 8.dp,
    // Phone landscape (research R9, landscape table; data-model.md §8).
    /** The least a landscape card row gets; rows grow to fill the card. */
    val stringCardHeightLandscape: Dp = 128.dp,
    /** The landscape settings column: 36 % of the content width within these, scaled by the font scale. */
    val landscapeSettingsMinWidth: Dp = 224.dp,
    val landscapeSettingsMaxWidth: Dp = 280.dp,
    /** Below this the landscape meter scrolls instead of shrinking further. */
    val meterMinWidth: Dp = 200.dp,
    // Metronome metrics from the Stitch metronome screens (feature 004, data-model.md §9, research R10).
    /** Time-signature and subdivision chip: `py-1.5` around `labelMedium` (a 44 dp target around it). */
    val chipHeight: Dp = 32.dp,
    /** Style pill: `py-1` around 11 px text (a 44 dp target around it). */
    val pillHeight: Dp = 28.dp,
    /** Accent cell bar box: `h-16`. */
    val accentCellBarHeight: Dp = 64.dp,
    /** Tempo dial on compact windows: `max-w-[270px]`; on expanded windows: `max-w-[420px]`. */
    val tempoDialMaxWidth: Dp = 270.dp,
    val tempoDialMaxWidthWide: Dp = 420.dp,
    /** Tempo stepper: `w-11 h-11`. */
    val tempoStepper: Dp = 44.dp,
    /** Tap tempo card: `h-16`; play button: `w-16 h-16`. */
    val tapTempoHeight: Dp = 64.dp,
    val playButton: Dp = 64.dp,
    /** Sound menu: `w-40`. */
    val soundMenuWidth: Dp = 160.dp,
    /** Sound tile on expanded windows and its glyph box (`w-7 h-7`). */
    val soundTileHeight: Dp = 44.dp,
    val soundTileGlyphBox: Dp = 28.dp,
    /** The wide layout's settings column: a third of the width, at least this. */
    val wideSettingsMinWidth: Dp = 320.dp,
)
