package audio.tembra.ui.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import audio.tembra.ui.theme.TembraTheme

/** A centred one-line empty-state message in the muted on-surface colour (spec FR-012). */
@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = TembraTheme.typography.bodyMedium,
            color = TembraTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("chrome.emptyState"),
        )
    }
}
