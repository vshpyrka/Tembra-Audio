package audio.tembra.ui.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import audio.tembra.ui.theme.TembraIcons
import audio.tembra.ui.theme.TembraShapes
import audio.tembra.ui.theme.TembraTheme

/**
 * The design's segmented selector (research R9, spec FR-002, FR-024, FR-025). Segments keep the drawn 32 dp or 28 dp
 * height while their interactive box is at least 44 dp (`minimumInteractiveComponentSize`); the container pill is
 * drawn behind them at its design height. Each segment is a tab with its label as description; hover raises an
 * inactive label to `onSurface`, keyboard focus draws a `primary` ring, and the active fill fades over the motion
 * token. Selecting the selected segment calls nothing. [widths] decides whether segments hug their labels or share the
 * caller's width equally. With a [testTag], every segment carries `<tag>.<id>` and its drawn surface `<tag>.<id>.surface`.
 */
@Composable
fun TembraSegmentedControl(
    items: List<SegmentSpec>,
    selectedId: String,
    onSelect: (String) -> Unit,
    style: SegmentedStyle,
    modifier: Modifier = Modifier,
    accent: SegmentAccent = SegmentAccent.Primary,
    widths: SegmentWidths = SegmentWidths.Content,
    testTag: String? = null,
) {
    val spacing = TembraTheme.spacing
    val colors = TembraTheme.colors
    val large = style == SegmentedStyle.Large
    val containerPadding = if (large) spacing.xs else spacing.xs / 2
    val containerRadius = if (large) TembraShapes.medium else TembraShapes.default
    val containerColor = if (large) colors.surfaceContainerLowest else colors.surfaceContainerLow
    val segmentHeight = if (large) spacing.segmentHeightLarge else spacing.segmentHeightSmall
    val gap = if (large) spacing.xs else spacing.xs / 2
    Row(
        modifier = modifier
            .testTagIfSet(testTag)
            .drawBehind {
                val height = (segmentHeight + containerPadding * 2).toPx()
                val top = (size.height - height) / 2
                drawRoundRect(
                    color = containerColor,
                    topLeft = Offset(0f, top),
                    size = Size(size.width, height),
                    cornerRadius = CornerRadius(containerRadius.toPx()),
                )
            }
            .padding(horizontal = containerPadding),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (item in items) {
            Segment(
                item = item,
                selected = item.id == selectedId,
                style = style,
                accent = accent,
                segmentHeight = segmentHeight,
                equalWidth = widths == SegmentWidths.Equal,
                onSelect = onSelect,
                testTag = testTag?.let { "$it.${item.id}" },
            )
        }
    }
}

@Composable
private fun RowScope.Segment(
    item: SegmentSpec,
    selected: Boolean,
    style: SegmentedStyle,
    accent: SegmentAccent,
    segmentHeight: Dp,
    equalWidth: Boolean,
    onSelect: (String) -> Unit,
    testTag: String?,
) {
    val spacing = TembraTheme.spacing
    val colors = TembraTheme.colors
    val motion = TembraTheme.motion
    val large = style == SegmentedStyle.Large
    val interactionSource = item.interactionSource ?: remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()

    val activeFill: Color
    val activeContent: Color
    if (large) {
        activeFill = colors.secondaryContainer
        activeContent = colors.onSecondaryContainer
    } else {
        when (accent) {
            SegmentAccent.Primary -> {
                activeFill = colors.primaryContainer
                activeContent = colors.onPrimaryContainer
            }
            SegmentAccent.Subtle -> {
                activeFill = colors.surfaceContainerHigh
                activeContent = colors.primary
            }
        }
    }
    val restingContent = if (hovered || pressed) colors.onSurface else colors.onSurfaceVariant
    val fill by animateColorAsState(
        targetValue = if (selected) activeFill else Color.Transparent,
        animationSpec = tween(motion.effectiveDuration, easing = motion.standardEasing),
        label = "segmentFill",
    )
    val content = if (selected) activeContent else restingContent
    val shapeRadius = if (large) TembraShapes.default else TembraShapes.small
    val textStyle: TextStyle = if (large) TembraTheme.typography.labelMedium else TembraTheme.typography.labelSmall
    val iconSize = if (large) spacing.segmentIconLarge else spacing.segmentIconSmall
    val horizontalPadding = if (large) spacing.segmentPaddingLarge else spacing.segmentPaddingSmall
    val ring = spacing.focusRingWidth
    val ringColor = colors.primary

    Box(
        modifier = Modifier
            .then(if (equalWidth) Modifier.weight(1f) else Modifier)
            .testTagIfSet(testTag)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = { if (!selected) onSelect(item.id) },
            )
            .semantics { contentDescription = item.label }
            .minimumInteractiveComponentSize(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .testTagIfSet(testTag?.let { "$it.surface" })
                .then(if (equalWidth) Modifier.fillMaxWidth() else Modifier)
                .height(segmentHeight)
                .drawBehind {
                    val radius = CornerRadius(shapeRadius.toPx())
                    drawRoundRect(color = fill, cornerRadius = radius)
                    if (focused) {
                        val width = ring.toPx()
                        drawRoundRect(
                            color = ringColor,
                            topLeft = Offset(width / 2, width / 2),
                            size = Size(size.width - width, size.height - width),
                            cornerRadius = CornerRadius(radius.x - width / 2),
                            style = Stroke(width = width),
                        )
                    }
                }
                .padding(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs + spacing.xs / 2, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (item.icon != null) {
                Icon(
                    imageVector = TembraIcons.vector(item.icon),
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(iconSize),
                )
            }
            Text(
                text = item.label,
                style = textStyle,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
