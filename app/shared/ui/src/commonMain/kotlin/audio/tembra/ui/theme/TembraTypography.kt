package audio.tembra.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Turns [TembraTypeScale] into Compose text styles with the bundled fonts. */
object TembraTypography {

    @Composable
    fun style(spec: TembraTextStyleSpec): TextStyle = TextStyle(
        fontFamily = TembraFonts.of(spec.family),
        fontSize = spec.sizeSp.sp,
        lineHeight = spec.lineHeightSp.sp,
        fontWeight = FontWeight(spec.weight),
        letterSpacing = spec.letterSpacingEm.em,
        fontFeatureSettings = if (spec.tabularFigures) "tnum" else null,
    )

    /** All twelve styles keyed by their theme.json names. */
    @Composable
    fun styles(): Map<String, TextStyle> = TembraTypeScale.byThemeName.mapValues { style(it.value) }

    @Composable
    fun material(): Typography = Typography(
        headlineLarge = style(TembraTypeScale.headlineLg),
        headlineMedium = style(TembraTypeScale.headlineMd),
        headlineSmall = style(TembraTypeScale.headlineSm),
        bodyLarge = style(TembraTypeScale.bodyLg),
        bodyMedium = style(TembraTypeScale.bodyMd),
        bodySmall = style(TembraTypeScale.bodySm),
        labelLarge = style(TembraTypeScale.labelLg),
        labelMedium = style(TembraTypeScale.labelMd),
        labelSmall = style(TembraTypeScale.labelSm),
    )

    @Composable
    fun extras(): TembraTypographyExtras = TembraTypographyExtras(
        headlineLgMobile = style(TembraTypeScale.headlineLgMobile),
        tabularTimecode = style(TembraTypeScale.tabularTimecode),
        tabularParam = style(TembraTypeScale.tabularParam),
        tunerNote = style(TembraTypeScale.tunerNote),
        tunerOctave = style(TembraTypeScale.tunerOctave),
        tunerCardNote = style(TembraTypeScale.tunerCardNote),
        tunerCardOctave = style(TembraTypeScale.tunerCardOctave),
        tunerReadout = style(TembraTypeScale.tunerReadout),
        microLabel = style(TembraTypeScale.microLabel),
        tempoNumber = style(TembraTypeScale.tempoNumber),
        sectionLabel = style(TembraTypeScale.sectionLabel),
    )
}
