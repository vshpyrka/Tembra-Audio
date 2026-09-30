package audio.tembra.ui.metronome.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import audio.tembra.ui.designsystem.drawFocusRing
import audio.tembra.ui.metronome.MetronomeCopy
import audio.tembra.ui.theme.TembraIcon
import audio.tembra.ui.theme.TembraIcons
import audio.tembra.ui.theme.TembraShapes
import audio.tembra.ui.theme.TembraTheme
import kotlinx.coroutines.launch

/**
 * The tap-tempo card (research R9, R10): 64 dp tall, `surfaceContainerHigh` (hovered or pressed `surfaceContainerHighest`, focus ring in `primary`), the
 * `touch_app` glyph and "TAP TEMPO" in `primary` over [caption]. Each tap reports the frame time of the frame it lands
 * in, from a user-event coroutine (`compose-state-and-effects`). The per-tap flash arrives with User Story 3.
 */
@Composable
internal fun TapTempoButton(
    caption: String,
    onTap: (frameNanos: Long) -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null,
    testTag: String = "metronome.tap",
) {
    val colors = TembraTheme.colors
    val spacing = TembraTheme.spacing
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    val scope = rememberCoroutineScope()
    val currentOnTap by rememberUpdatedState(onTap)
    val ringColor = colors.primary
    val ring = spacing.focusRingWidth

    Column(
        modifier = modifier
            .testTag(testTag)
            .height(spacing.tapTempoHeight)
            .clip(RoundedCornerShape(TembraShapes.medium))
            .background(if (pressed || hovered) colors.surfaceContainerHighest else colors.surfaceContainerHigh)
            .drawWithContent {
                drawContent()
                if (focused) drawFocusRing(ringColor, ring.toPx(), CornerRadius(TembraShapes.medium.toPx()))
            }
            .clickable(interactionSource = source, indication = null, role = Role.Button) {
                scope.launch { withFrameNanos { currentOnTap(it) } }
            }
            .semantics {
                contentDescription = MetronomeCopy.tapTempo
                stateDescription = caption
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = TembraIcons.vector(TembraIcon.TouchApp),
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(MetronomeDefaults.TapIconSize),
            )
            Text(
                text = MetronomeCopy.tapTempo.uppercase(),
                style = TembraTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = colors.primary,
                maxLines = 1,
            )
        }
        Text(text = caption, style = TembraTheme.typography.labelSmall, color = colors.outline, maxLines = 1)
    }
}
