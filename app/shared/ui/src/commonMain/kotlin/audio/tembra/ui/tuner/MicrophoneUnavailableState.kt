package audio.tembra.ui.tuner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import audio.tembra.ui.theme.TembraIcon
import audio.tembra.ui.theme.TembraIcons
import audio.tembra.ui.theme.TembraTheme

/**
 * Replaces the meter on a device with no microphone (feature 011, spec FR-002): what is missing and what still works,
 * with no button, since there is nothing to allow. Laid out like [MicrophoneDeniedState].
 */
@Composable
internal fun MicrophoneUnavailableState(modifier: Modifier = Modifier) {
    val spacing = TembraTheme.spacing
    val colors = TembraTheme.colors
    Column(
        modifier = modifier.testTag("tuner.noMicrophone"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Icon(
            imageVector = TembraIcons.vector(TembraIcon.MicOff),
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(spacing.deniedIconSize),
        )
        Text(text = TunerCopy.unavailableTitle, style = TembraTheme.typography.headlineSmall, color = colors.onSurface, textAlign = TextAlign.Center)
        Text(
            text = TunerCopy.unavailableText,
            style = TembraTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = spacing.deniedTextMaxWidth),
        )
    }
}
