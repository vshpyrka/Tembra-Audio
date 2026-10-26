package audio.tembra.ui.timeline.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import audio.tembra.ui.designsystem.TembraTooltip
import audio.tembra.ui.designsystem.drawFocusRing
import audio.tembra.ui.theme.TembraIcon
import audio.tembra.ui.theme.TembraIcons
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.TimelineCopy
import audio.tembra.ui.timeline.TimelineTestTags

/**
 * The zoom buttons for the ruler's right end (spec FR-015, D1): "−", the level, "+" on one 32 dp `secondaryContainer`
 * band, each a 44 dp target (FR-056), 132 dp in all. "−" and "+" (tags `timeline.zoom.out` and `timeline.zoom.in`)
 * step the zoom and show their names as tooltips; the level (tag `timeline.zoom.level`, "Zoom 2.5×, fit the project")
 * fits the project on a tap. A segment rises to `secondary` under a pointer or a press and draws the `primary` focus
 * ring. [levelLabel] is the caller's [zoomLevelLabel].
 */
@Composable
internal fun ZoomButtons(
    levelLabel: String,
    onZoomOut: () -> Unit,
    onZoomIn: () -> Unit,
    onFit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = TembraTheme.spacing
    val band = TembraTheme.colors.secondaryContainer
    val bandHeight = spacing.chipHeight
    Row(
        modifier = modifier
            .width(spacing.touchTargetMin * SEGMENTS)
            .height(spacing.touchTargetMin)
            .drawBehind { drawBand(band, bandHeight.toPx()) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZoomSegment(description = TimelineCopy.zoomOut, tooltip = TimelineCopy.zoomOut, onClick = onZoomOut, testTag = TimelineTestTags.ZOOM_OUT) { tint ->
            Icon(imageVector = TembraIcons.vector(TembraIcon.Remove), contentDescription = null, tint = tint, modifier = Modifier.size(spacing.pickerIconSize))
        }
        ZoomSegment(description = TimelineCopy.zoomLevel(levelLabel), tooltip = TimelineCopy.fitProject, onClick = onFit, testTag = TimelineTestTags.ZOOM_LEVEL) { tint ->
            // The segment's description already names the level: the drawn text stays out of the merged node.
            Text(text = levelLabel, style = TembraTheme.typographyExtras.tabularParam, color = tint, maxLines = 1, softWrap = false, modifier = Modifier.clearAndSetSemantics {})
        }
        ZoomSegment(description = TimelineCopy.zoomIn, tooltip = TimelineCopy.zoomIn, onClick = onZoomIn, testTag = TimelineTestTags.ZOOM_IN) { tint ->
            Icon(imageVector = TembraIcons.vector(TembraIcon.Add), contentDescription = null, tint = tint, modifier = Modifier.size(spacing.pickerIconSize))
        }
    }
}

/** One 44 dp target over the band: its own hover and press highlight in the band's height, focus ring and tooltip. */
@Composable
private fun ZoomSegment(
    description: String,
    tooltip: String,
    onClick: () -> Unit,
    testTag: String,
    content: @Composable (tint: Color) -> Unit,
) {
    val colors = TembraTheme.colors
    val spacing = TembraTheme.spacing
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    val raised = hovered || pressed
    val fill = colors.secondary
    val tint = if (raised) colors.onSecondary else colors.onSecondaryContainer
    val ringColor = colors.primary
    val ring = spacing.focusRingWidth
    val bandHeight = spacing.chipHeight
    TembraTooltip(text = tooltip, interactionSource = source) {
        Box(
            modifier = Modifier
                .size(spacing.touchTargetMin)
                .drawBehind {
                    val top = (size.height - bandHeight.toPx()) / 2
                    // The highlight and the ring follow the band, not the taller target.
                    inset(left = 0f, top = top, right = 0f, bottom = top) {
                        val radius = CornerRadius(size.height / 2)
                        if (raised) drawRoundRect(color = fill, cornerRadius = radius)
                        if (focused) drawFocusRing(ringColor, ring.toPx(), radius)
                    }
                }
                .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description }
                .testTag(testTag),
            contentAlignment = Alignment.Center,
        ) {
            content(tint)
        }
    }
}

/** The 32 dp band behind the three targets, centred in their 44 dp height. */
private fun DrawScope.drawBand(color: Color, height: Float) {
    val top = (size.height - height) / 2
    drawRoundRect(color = color, topLeft = Offset(0f, top), size = Size(size.width, height), cornerRadius = CornerRadius(height / 2))
}

private const val SEGMENTS = 3
