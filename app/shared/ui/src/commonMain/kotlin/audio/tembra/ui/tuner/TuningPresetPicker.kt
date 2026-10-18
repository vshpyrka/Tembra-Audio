package audio.tembra.ui.tuner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import audio.tembra.core.tuner.TuningPreset
import audio.tembra.ui.theme.TembraIcon
import audio.tembra.ui.theme.TembraIcons
import audio.tembra.ui.theme.TembraShapes
import audio.tembra.ui.theme.TembraTheme

/** The "Tuning Preset" button and its menu (spec FR-003, FR-017, research R9); every preset is listed by name and strings. */
@Composable
internal fun TuningPresetPicker(current: TuningPreset, presets: List<TuningPreset>, onPreset: (String) -> Unit, modifier: Modifier = Modifier) {
    val spacing = TembraTheme.spacing
    val colors = TembraTheme.colors
    var open by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(TembraShapes.medium)
    val border = when {
        focused -> colors.primary
        hovered -> colors.outlineVariant.copy(alpha = HOVER_BORDER_ALPHA)
        else -> colors.outlineVariant.copy(alpha = BORDER_ALPHA)
    }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .testTag("tuner.preset.picker")
                .heightIn(min = spacing.touchTargetMin)
                .background(colors.surfaceContainerLow, shape)
                .border(if (focused) spacing.focusRingWidth else spacing.seam, border, shape)
                .clickable(interactionSource = interactionSource, indication = null, role = Role.Button) { open = true }
                .semantics { contentDescription = "${TunerCopy.tuningPreset}, ${current.name}" }
                .padding(horizontal = spacing.md, vertical = spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = TembraIcons.vector(TembraIcon.MusicNote), contentDescription = null, tint = colors.primary, modifier = Modifier.size(spacing.pickerIconSize))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = TunerCopy.tuningPreset.uppercase(), style = TembraTheme.typographyExtras.microLabel, color = colors.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = "${current.name} (${current.summary})",
                    style = TembraTheme.typography.labelMedium,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(imageVector = TembraIcons.vector(TembraIcon.ExpandMore), contentDescription = null, tint = colors.outlineVariant, modifier = Modifier.size(spacing.pickerIconSize))
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            // Bounded height: see MetronomeDefaults.MenuMaxHeight (iOS popup layers can offer infinite height).
            modifier = Modifier.heightIn(max = MenuMaxHeight),
        ) {
            for (preset in presets) {
                val selected = preset.id == current.id
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(text = preset.name, style = TembraTheme.typography.labelLarge, color = if (selected) colors.primary else colors.onSurface)
                            Text(text = preset.summary, style = TembraTheme.typographyExtras.tabularParam, color = colors.onSurfaceVariant)
                        }
                    },
                    onClick = {
                        open = false
                        if (!selected) onPreset(preset.id)
                    },
                    modifier = Modifier.testTag("tuner.preset.item.${preset.id}").semantics { this.selected = selected },
                )
            }
        }
    }
}

private const val BORDER_ALPHA = 0.3f
private const val HOVER_BORDER_ALPHA = 0.6f

/** Same bound as the metronome menus: Compose's iOS popup layer can measure a dropdown with infinite height. */
private val MenuMaxHeight = 360.dp
