package audio.tembra.ui.theme

/** The twelve text styles of design/stitch/theme.json → typography (px → sp, em → em). */
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
