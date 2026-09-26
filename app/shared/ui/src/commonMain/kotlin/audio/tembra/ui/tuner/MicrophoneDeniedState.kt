package audio.tembra.ui.tuner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
 * Replaces the meter when microphone access was denied (spec FR-015, contracts/microphone-permission.md): one short
 * explanation and one button; the ViewModel decides whether it re-requests or opens the settings.
 */
@Composable
internal fun MicrophoneDeniedState(onRequestMicrophone: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = TembraTheme.spacing
    val colors = TembraTheme.colors
    Column(
        modifier = modifier.testTag("tuner.denied"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Icon(
            imageVector = TembraIcons.vector(TembraIcon.MicOff),
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(spacing.deniedIconSize),
        )
        Text(text = TunerCopy.deniedTitle, style = TembraTheme.typography.headlineSmall, color = colors.onSurface, textAlign = TextAlign.Center)
        Text(
            text = TunerCopy.deniedText,
            style = TembraTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = spacing.deniedTextMaxWidth),
        )
        Button(
            onClick = onRequestMicrophone,
            colors = ButtonDefaults.buttonColors(containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer),
            modifier = Modifier.heightIn(min = spacing.touchTargetMin).testTag("tuner.denied.action"),
        ) {
            Text(text = TunerCopy.deniedButton, style = TembraTheme.typography.labelLarge)
        }
    }
}
