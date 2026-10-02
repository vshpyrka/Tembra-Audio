package audio.tembra.ui.metronome.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import audio.tembra.ui.theme.TembraTheme

/**
 * A metronome section's label (contracts/metronome-screen.md, Copy): [text] is the sentence-case copy ("Time
 * signature"), drawn in capitals in the `sectionLabel` style and `outline` colour, and announced as a heading in
 * sentence case, as feature 003's status pill does. It wraps rather than cuts at the largest font sizes.
 */
@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = TembraTheme.typographyExtras.sectionLabel,
        color = TembraTheme.colors.outline,
        modifier = modifier.semantics {
            contentDescription = text
            heading()
        },
    )
}
