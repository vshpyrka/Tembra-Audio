package audio.tembra.ui.theme

/**
 * The twelve text styles of design/stitch/theme.json → typography (px → sp, em → em), plus the tuner's and the
 * metronome's screen-derived styles (research R9 of feature 003, R10 of feature 004), which have no theme.json entry and
 * stay out of [byThemeName].
 */
object TembraTypeScale {
    val headlineLg = TembraTextStyleSpec(TembraFont.SpaceGrotesk, 32f, 40f, 600, -0.02f)
    val headlineLgMobile = TembraTextStyleSpec(TembraFont.SpaceGrotesk, 24f, 32f, 600, -0.01f)
    val headlineMd = TembraTextStyleSpec(TembraFont.SpaceGrotesk, 22f, 28f, 600, -0.01f)
    val headlineSm = TembraTextStyleSpec(TembraFont.SpaceGrotesk, 18f, 24f, 600)
    val bodyLg = TembraTextStyleSpec(TembraFont.Inter, 16f, 24f, 400)
    val bodyMd = TembraTextStyleSpec(TembraFont.Inter, 14f, 20f, 400)
    val bodySm = TembraTextStyleSpec(TembraFont.Inter, 12f, 16f, 400)
    val labelLg = TembraTextStyleSpec(TembraFont.SpaceGrotesk, 14f, 18f, 500, 0.01f)
    val labelMd = TembraTextStyleSpec(TembraFont.SpaceGrotesk, 12f, 16f, 500, 0.02f)
    val labelSm = TembraTextStyleSpec(TembraFont.SpaceGrotesk, 10f, 14f, 600, 0.04f)
    val tabularTimecode = TembraTextStyleSpec(TembraFont.SpaceGrotesk, 15f, 20f, 500, 0.05f, tabularFigures = true)
    val tabularParam = TembraTextStyleSpec(TembraFont.Inter, 11f, 14f, 500, 0.02f, tabularFigures = true)

    // Tuner styles from the Stitch tuner screens (feature 003, data-model.md §8, research R9).
    /** Heard note: `text-[64px] font-bold` (Space Grotesk). */
    val tunerNote = TembraTextStyleSpec(TembraFont.SpaceGrotesk, 64f, 64f, 700, -0.02f)
    /** Octave beside the note: `text-[32px] font-bold`. */
    val tunerOctave = TembraTextStyleSpec(TembraFont.SpaceGrotesk, 32f, 32f, 700)
    /** By Ear card note: `text-[32px] font-bold`. */
    val tunerCardNote = TembraTextStyleSpec(TembraFont.SpaceGrotesk, 32f, 32f, 700)
    /** By Ear card octave: `text-[20px]` bold. */
    val tunerCardOctave = TembraTextStyleSpec(TembraFont.SpaceGrotesk, 20f, 20f, 700)
    /** Live frequency readout: `font-tabular-timecode text-[18px] font-semibold tracking-wider`. */
    val tunerReadout = TembraTextStyleSpec(TembraFont.SpaceGrotesk, 18f, 24f, 600, 0.05f, tabularFigures = true)
    /** Chip and card overlines: `text-[11px] uppercase font-semibold tracking-wider`. */
    val microLabel = TembraTextStyleSpec(TembraFont.SpaceGrotesk, 11f, 14f, 600, 0.04f)

    // Metronome styles from the Stitch metronome screens (feature 004, data-model.md §9, research R10).
    /** Dial tempo: `text-[64px] font-bold tracking-tighter`, tabular so the number does not jitter while dragging. */
    val tempoNumber = TembraTextStyleSpec(TembraFont.SpaceGrotesk, 64f, 64f, 700, -0.05f, tabularFigures = true)
    /** Section overlines ("TIME SIGNATURE", "STYLE"): `text-[10px] font-semibold tracking-wider`, uppercase in use. */
    val sectionLabel = TembraTextStyleSpec(TembraFont.SpaceGrotesk, 10f, 14f, 600, 0.05f)

    /** Keyed by the theme.json names, in the file's order. */
    val byThemeName: Map<String, TembraTextStyleSpec> = mapOf(
        "headline-lg" to headlineLg,
        "headline-lg-mobile" to headlineLgMobile,
        "headline-md" to headlineMd,
        "headline-sm" to headlineSm,
        "body-lg" to bodyLg,
        "body-md" to bodyMd,
        "body-sm" to bodySm,
        "label-lg" to labelLg,
        "label-md" to labelMd,
        "label-sm" to labelSm,
        "tabular-timecode" to tabularTimecode,
        "tabular-param" to tabularParam,
    )
}
