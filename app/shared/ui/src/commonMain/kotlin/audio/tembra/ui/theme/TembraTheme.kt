package audio.tembra.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/** The single design system (always dark; the design defines only a dark palette). */
@Composable
fun TembraTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalDawColors provides DawColors.Default,
        LocalTembraSpacing provides TembraSpacing(),
        LocalTembraTypographyExtras provides TembraTypography.extras(),
        LocalTembraMotion provides TembraMotion(reduceMotion = rememberReduceMotion()),
        LocalTembraGradients provides TembraGradients.Default,
    ) {
        MaterialTheme(
            colorScheme = TembraColorScheme.dark,
            typography = TembraTypography.material(),
            shapes = TembraShapes.material,
            content = content,
        )
    }
}

/** Accessors for the current theme values (contracts/design-system-api.md). */
object TembraTheme {
    val colors: ColorScheme
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme
    val daw: DawColors
        @Composable @ReadOnlyComposable get() = LocalDawColors.current
    val typography: Typography
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography
    val typographyExtras: TembraTypographyExtras
        @Composable @ReadOnlyComposable get() = LocalTembraTypographyExtras.current
    val shapes: Shapes
        @Composable @ReadOnlyComposable get() = MaterialTheme.shapes
    val spacing: TembraSpacing
        @Composable @ReadOnlyComposable get() = LocalTembraSpacing.current
    val motion: TembraMotion
        @Composable @ReadOnlyComposable get() = LocalTembraMotion.current
    val gradients: TembraGradients
        @Composable @ReadOnlyComposable get() = LocalTembraGradients.current
}
