package audio.tembra.ui.metronome.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.ui.designsystem.drawFocusRing
import audio.tembra.ui.metronome.MetronomeCopy
import audio.tembra.ui.theme.TembraIcon
import audio.tembra.ui.theme.TembraIcons
import audio.tembra.ui.theme.TembraShapes
import audio.tembra.ui.theme.TembraTheme

/**
 * The compact layout's time signature (design iteration D1, contracts/design-system-additions.md): a fully rounded
 * pill in the selected-chip accent (`secondaryContainer`, hover `secondary`) naming the [selected] signature with an
 * `expand_more` chevron, drawn 32 dp tall inside a 44 dp target and announced "Time signature, 4/4". It opens a menu on
 * `surfaceContainerHighest` listing the six signatures in the spec's order with a check on the current one; choosing
 * another calls [onSelect], the current one selects nothing again. The pill carries [testTag], its drawn label
 * `<testTag>.label` and each entry `<testTag>.<id>` (`metronome.signature.7-8`), the same tags as the chips it replaces
 * on a phone, with the check `<testTag>.<id>.check`. Keyboard focus draws the `primary` ring; Enter opens the menu.
 */
@Composable
internal fun TimeSignatureMenu(
    selected: TimeSignature,
    onSelect: (TimeSignature) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "metronome.signature",
) {
    val colors = TembraTheme.colors
    val spacing = TembraTheme.spacing
    var open by remember { mutableStateOf(false) }
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    val raised = hovered || pressed
    val fill = if (raised) colors.secondary else colors.secondaryContainer
    val content = if (raised) colors.onSecondary else colors.onSecondaryContainer
    val ring = spacing.focusRingWidth
    val ringColor = colors.primary

    Box(modifier = modifier) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides spacing.touchTargetMin) {
            Box(
                modifier = Modifier
                    .testTag(testTag)
                    .clickable(interactionSource = source, indication = null, role = Role.Button) { open = true }
                    .semantics { contentDescription = MetronomeCopy.timeSignatureOf(selected.label) }
                    .minimumInteractiveComponentSize(),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = Modifier
                        .heightIn(min = spacing.chipHeight)
                        .drawBehind {
                            val radius = CornerRadius(size.height / 2)
                            drawRoundRect(color = fill, cornerRadius = radius)
                            if (focused) drawFocusRing(ringColor, ring.toPx(), radius)
                        }
                        .padding(start = spacing.md, end = spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The pill's description already names the signature: the drawn label stays out of the merged node.
                    Text(
                        text = selected.label,
                        style = TembraTheme.typography.labelMedium,
                        color = content,
                        maxLines = 1,
                        modifier = Modifier.testTag("$testTag.label").clearAndSetSemantics {},
                    )
                    Icon(
                        imageVector = TembraIcons.vector(TembraIcon.ExpandMore),
                        contentDescription = null,
                        tint = content,
                        modifier = Modifier.size(MetronomeDefaults.SoundPillChevronSize),
                    )
                }
            }
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.width(spacing.soundMenuWidth),
            shape = RoundedCornerShape(TembraShapes.medium),
            containerColor = colors.surfaceContainerHighest,
        ) {
            for (signature in TimeSignature.entries) {
                val current = signature == selected
                DropdownMenuItem(
                    text = {
                        Text(
                            text = signature.label,
                            style = TembraTheme.typography.labelMedium,
                            color = if (current) colors.primary else colors.onSurfaceVariant,
                            maxLines = 1,
                        )
                    },
                    onClick = {
                        open = false
                        if (!current) onSelect(signature)
                    },
                    modifier = Modifier
                        .testTag("$testTag.${signature.id}")
                        .semantics { this.selected = current },
                    trailingIcon = if (current) {
                        {
                            Icon(
                                imageVector = TembraIcons.vector(TembraIcon.Check),
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(MetronomeDefaults.SoundGlyphSize).testTag("$testTag.${signature.id}.check"),
                            )
                        }
                    } else {
                        null
                    },
                    contentPadding = PaddingValues(horizontal = spacing.md),
                )
            }
        }
    }
}
