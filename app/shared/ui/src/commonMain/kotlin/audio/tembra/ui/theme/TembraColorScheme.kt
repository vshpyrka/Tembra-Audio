package audio.tembra.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

/** Material 3 colour roles transcribed from design/stitch/theme.json → namedColors (dark only). */
object TembraColorScheme {
    val dark: ColorScheme = darkColorScheme(
        primary = Color(0xFFCEBDFF),
        onPrimary = Color(0xFF381385),
        primaryContainer = Color(0xFFA78BFA),
        onPrimaryContainer = Color(0xFF3C1989),
        inversePrimary = Color(0xFF674BB5),
        secondary = Color(0xFFFFAFD3),
        onSecondary = Color(0xFF620040),
        secondaryContainer = Color(0xFF85145A),
        onSecondaryContainer = Color(0xFFFF93C8),
        tertiary = Color(0xFFD0BCFF),
        onTertiary = Color(0xFF3C0091),
        tertiaryContainer = Color(0xFFAB88FF),
        onTertiaryContainer = Color(0xFF40009B),
        background = Color(0xFF131316),
        onBackground = Color(0xFFE4E1E6),
        surface = Color(0xFF131316),
        onSurface = Color(0xFFE4E1E6),
        surfaceVariant = Color(0xFF353438),
        onSurfaceVariant = Color(0xFFCAC4D4),
        surfaceTint = Color(0xFFCEBDFF),
        inverseSurface = Color(0xFFE4E1E6),
        inverseOnSurface = Color(0xFF303033),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        outline = Color(0xFF948E9D),
        outlineVariant = Color(0xFF494552),
        surfaceBright = Color(0xFF39393C),
        surfaceDim = Color(0xFF131316),
        surfaceContainer = Color(0xFF1F1F22),
        surfaceContainerHigh = Color(0xFF2A2A2D),
        surfaceContainerHighest = Color(0xFF353438),
        surfaceContainerLow = Color(0xFF1B1B1E),
        surfaceContainerLowest = Color(0xFF0E0E11),
        primaryFixed = Color(0xFFE8DDFF),
        primaryFixedDim = Color(0xFFCEBDFF),
        onPrimaryFixed = Color(0xFF21005E),
        onPrimaryFixedVariant = Color(0xFF4F319C),
        secondaryFixed = Color(0xFFFFD8E7),
        secondaryFixedDim = Color(0xFFFFAFD3),
        onSecondaryFixed = Color(0xFF3D0026),
        onSecondaryFixedVariant = Color(0xFF85145A),
        tertiaryFixed = Color(0xFFE9DDFF),
        tertiaryFixedDim = Color(0xFFD0BCFF),
        onTertiaryFixed = Color(0xFF23005C),
        onTertiaryFixedVariant = Color(0xFF5516BE),
    )
}
