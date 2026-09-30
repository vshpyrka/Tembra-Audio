package audio.tembra.ui.metronome.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import audio.tembra.core.metronome.MetronomeStyle
import audio.tembra.ui.designsystem.TembraPill
import audio.tembra.ui.metronome.MetronomeCopy
import audio.tembra.ui.theme.TembraTheme

/**
 * The style presets under the beat cells (spec FR-010, research R10): a `surfaceContainer` seam, the "STYLE" label and
 * one [TembraPill] per [MetronomeStyle], wrapping to further rows when they do not fit. The [selected] style's pill
 * is selected; with null (cells edited by hand) none is. A click on the selected pill is ignored. Each pill is tagged `<testTag>.<id>` (`metronome.style.standard`).
 */
@Composable
internal fun StyleRow(
    selected: MetronomeStyle?,
    onSelect: (MetronomeStyle) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "metronome.style",
) {
    val spacing = TembraTheme.spacing
    Column(modifier = modifier.testTag(testTag)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(spacing.seam)
                .background(TembraTheme.colors.surfaceContainer),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = spacing.xs, top = spacing.xs, end = spacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel(text = MetronomeCopy.style, modifier = Modifier.padding(end = spacing.sm))
            // The pills wrap under one another rather than overlap when large fonts widen them (spec FR-028).
            FlowRow(
                modifier = Modifier.weight(1f, fill = false),
                horizontalArrangement = Arrangement.spacedBy(MetronomeDefaults.PillGap, Alignment.End),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                for (style in MetronomeStyle.entries) {
                    val isSelected = style == selected
                    TembraPill(
                        text = style.label,
                        selected = isSelected,
                        onClick = { if (!isSelected) onSelect(style) },
                        testTag = "$testTag.${style.id}",
                    )
                }
            }
        }
    }
}
