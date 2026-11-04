package audio.tembra.ui.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import audio.tembra.ui.theme.TembraIcons
import audio.tembra.ui.theme.TembraShapes
import audio.tembra.ui.theme.TembraTheme

/**
 * The metronome's equal-width chip grid (research R10, contracts/design-system-additions.md): [columns] chips per row,
 * wrapping into further rows, each row's chips sharing its width equally (a short last row keeps the same widths).
 * When a chip would be too narrow for its whole label (the largest font sizes), the row holds fewer chips, in as few
 * balanced rows as fit, rather than cutting labels short (spec FR-028).
 * Chips keep their drawn 32 dp height (growing only with large fonts) inside a 44 dp target
 * (`minimumInteractiveComponentSize` at the project's `touchTargetMin` rather than Material's 48 dp); each is a
 * tab with `selected` semantics. The selected chip fills with the [accent]; the others sit on `surfaceContainerHigh`
 * with an `onSurfaceVariant` label that hover raises to `onSurface`; keyboard focus draws a `primary` ring. Selecting
 * the selected chip calls nothing. With a [testTag], every chip carries `<tag>.<id>`, its drawn surface
 * `<tag>.<id>.surface` and its glyph `<tag>.<id>.icon`.
 */
@Composable
fun TembraChipRow(
    items: List<ChipSpec>,
    selectedId: String,
    onSelect: (String) -> Unit,
    accent: ChipAccent,
    modifier: Modifier = Modifier,
    columns: Int = items.size,
    testTag: String? = null,
) {
    val spacing = TembraTheme.spacing
    val gap = spacing.xs + spacing.xs / 2
    val chipWidth = rememberWidestChip(items)
    BoxWithConstraints(modifier = modifier.testTagIfSet(testTag)) {
        val perRow = chipsPerRow(items.size, columns.coerceAtLeast(1), maxWidth, chipWidth, gap)
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides spacing.touchTargetMin) {
                for (row in items.chunked(perRow)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        for (item in row) {
                            Chip(
                                item = item,
                                selected = item.id == selectedId,
                                accent = accent,
                                onSelect = onSelect,
                                testTag = testTag?.let { "$it.${item.id}" },
                            )
                        }
                        repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** The width the widest chip needs to show its glyph and whole label at the current font scale. */
@Composable
private fun rememberWidestChip(items: List<ChipSpec>): Dp {
    val spacing = TembraTheme.spacing
    val style = TembraTheme.typography.labelMedium
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(items, style, density, measurer) {
        val widest = items.maxOfOrNull { item ->
            val label = with(density) { measurer.measure(item.label, style, maxLines = 1).size.width.toDp() }
            if (item.icon != null) label + spacing.segmentIconLarge + spacing.xs else label
        } ?: 0.dp
        // One dp of slack: the row splits its width into whole pixels per chip.
        widest + spacing.xs * 2 + 1.dp
    }
}

/**
 * [columns] chips per row while each keeps [chipWidth]; otherwise as few rows as fit, balanced so the rows hold as
 * equal a number of chips as they can (six signatures become two rows of three at the largest font sizes).
 */
private fun chipsPerRow(count: Int, columns: Int, width: Dp, chipWidth: Dp, gap: Dp): Int {
    val fit = ((width + gap) / (chipWidth + gap)).toInt().coerceAtLeast(1)
    if (fit >= columns) return columns
    val rows = (count + fit - 1) / fit
    return (count + rows - 1) / rows
}

@Composable
private fun RowScope.Chip(
    item: ChipSpec,
    selected: Boolean,
    accent: ChipAccent,
    onSelect: (String) -> Unit,
    testTag: String?,
) {
    val spacing = TembraTheme.spacing
    val colors = TembraTheme.colors
    val motion = TembraTheme.motion
    val interactionSource = item.interactionSource ?: remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()

    val activeFill = when (accent) {
        ChipAccent.Secondary -> colors.secondaryContainer
        ChipAccent.Primary -> colors.primaryContainer
    }
    val activeContent = when (accent) {
        ChipAccent.Secondary -> colors.onSecondaryContainer
        ChipAccent.Primary -> colors.onPrimaryContainer
    }
    val fill by animateColorAsState(
        targetValue = if (selected) activeFill else colors.surfaceContainerHigh,
        animationSpec = tween(motion.effectiveDuration, easing = motion.standardEasing),
        label = "chipFill",
    )
    val content = when {
        selected -> activeContent
        hovered || pressed -> colors.onSurface
        else -> colors.onSurfaceVariant
    }
    val ring = spacing.focusRingWidth
    val ringColor = colors.primary

    Box(
        modifier = Modifier
            .weight(1f)
            .testTagIfSet(testTag)
            .focusIdentity(testTag)
            .hoverArea(hovered, TembraShapes.small)
            .pointerHoverIcon(PointerIcon.Hand).selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = { if (!selected) onSelect(item.id) },
            )
            .minimumInteractiveComponentSize(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .testTagIfSet(testTag?.let { "$it.surface" })
                .fillMaxWidth()
                .heightIn(min = spacing.chipHeight)
                .drawBehind {
                    val radius = CornerRadius(TembraShapes.default.toPx())
                    drawRoundRect(color = fill, cornerRadius = radius)
                    if (focused) drawFocusRing(ringColor, ring.toPx(), radius)
                }
                .padding(horizontal = spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (item.icon != null) {
                Icon(
                    imageVector = TembraIcons.vector(item.icon),
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier
                        .testTagIfSet(testTag?.let { "$it.icon" })
                        .size(spacing.segmentIconLarge),
                )
            }
            Text(
                text = item.label,
                style = TembraTheme.typography.labelMedium,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
