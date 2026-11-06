package audio.tembra.ui.tracksettings

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import audio.tembra.core.project.Pan
import audio.tembra.ui.designsystem.TembraSlider
import audio.tembra.ui.designsystem.TembraSliderDefaults
import audio.tembra.ui.designsystem.TembraSliderThumb
import audio.tembra.ui.theme.TembraTheme
import kotlin.math.roundToInt

/**
 * The track's pan in the settings form (feature 012, spec FR-007; contracts/mixer-ui.md "PanSlider"): a slider over
 * −100 … 100 in whole percent on the design system's track and handle, hard left at its left end, the centre in the
 * middle, with the readout ("L 35", "C", "R 20") after it. Reads "Pan, L 35" and keeps Material's range semantics for
 * `setProgress`; changes reach [onPan] while dragging, the release [onSettled] (one undo step, FR-005).
 */
@Composable
internal fun PanSlider(
    pan: Pan,
    onPan: (Pan) -> Unit,
    onSettled: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null,
    testTag: String? = null,
) {
    val colors = TembraTheme.colors
    val spacing = TembraTheme.spacing
    val source = interactionSource ?: remember { MutableInteractionSource() }
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalAlignment = Alignment.CenterVertically) {
        TembraSlider(
            value = pan.percent.toFloat(),
            onValueChange = { raw ->
                val next = Pan.clamped(raw.roundToInt())
                if (next != pan) onPan(next)
            },
            modifier = Modifier
                .weight(1f)
                .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
                .semantics {
                    contentDescription = TrackSettingsCopy.pan
                    stateDescription = pan.readout
                },
            valueRange = -Pan.MAX.toFloat()..Pan.MAX.toFloat(),
            steps = 2 * Pan.MAX - 1,
            onValueChangeFinished = onSettled,
            interactionSource = source,
            thumb = { TembraSliderThumb(source) },
            track = {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(TembraSliderDefaults.TrackHeight)
                        .background(colors.surfaceContainerHighest, CircleShape),
                )
            },
        )
        Text(
            text = pan.readout,
            style = TembraTheme.typographyExtras.tabularParam,
            color = colors.onSurface,
            maxLines = 1,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = ReadoutMinWidth),
        )
    }
}

private val ReadoutMinWidth = 48.dp
