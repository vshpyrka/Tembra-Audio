package audio.tembra.ui.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import audio.tembra.ui.theme.TembraIcons
import audio.tembra.ui.theme.TembraTheme

/**
 * The navigation rail for windows from 600 dp (spec FR-003, FR-005, FR-019): 64 dp wide on surfaceContainerLowest,
 * filling the window's height (the shell has no top bar), its items clear of the status bar, a desktop caption, a
 * start-side cutout and the gesture area; items are rounded squares
 * at least 48 x 48 dp that grow to their label within the rail (so "Metronome" stays on one line), with the glyph
 * above a labelSmall label. Vertical metrics are 4 dp so six items fit a phone in landscape (411 dp tall) with no
 * item off screen; shorter windows scroll the rail. The active item is secondaryContainer with onSecondaryContainer
 * content and a bold label, drawn behind the content without clipping; the fill fades between items over the motion
 * token. Hover or a press fills an inactive item with surfaceContainerHigh and raises its content to onSurface,
 * keyboard focus draws a primary ring, and every item is a tab with its label as description (spec FR-014, FR-015).
 * [itemsAlignment] places the items in the rail's height: at the top by default, at the bottom in the iPhone Duo
 * experiment's end-edge rail, below the status bar and camera that share its column.
 */
@Composable
fun TembraNavigationRail(
    items: List<NavigationItemSpec>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    itemsAlignment: Alignment.Vertical = Alignment.Top,
) {
    val spacing = TembraTheme.spacing
    Surface(
        modifier = modifier
            .testTag("chrome.rail")
            .fillMaxHeight()
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Start))
            .width(spacing.railWidth),
        color = TembraTheme.colors.surfaceContainerLowest,
    ) {
        // The box holds the scrolling column at [itemsAlignment]: a scrolling column alone has no height to align in.
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top).union(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))),
            contentAlignment = BiasAlignment(horizontalBias = 0f, verticalBias = if (itemsAlignment == Alignment.Bottom) 1f else -1f),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = spacing.xs),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                for (item in items) {
                    NavigationRailItem(item = item, selected = item.id == selectedId, onSelect = onSelect)
                }
            }
        }
    }
}

@Composable
private fun NavigationRailItem(
    item: NavigationItemSpec,
    selected: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = TembraTheme.spacing
    val colors = TembraTheme.colors
    val motion = TembraTheme.motion
    val shape = TembraTheme.shapes.large
    val interactionSource = item.interactionSource ?: remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    // Read in composition only for the content colour, which Icon and Text take as a value; the fill reads it in draw.
    val active = animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(motion.effectiveDuration, easing = motion.standardEasing),
        label = "navRailItemActive",
    )
    val restingFill = if (hovered || pressed) colors.surfaceContainerHigh else Color.Transparent
    val activeFill = colors.secondaryContainer
    val restingContent = if (hovered || pressed) colors.onSurface else colors.onSurfaceVariant
    val content = lerp(restingContent, colors.onSecondaryContainer, active.value)
    Column(
        modifier = modifier
            .sizeIn(minWidth = spacing.railItemWidth, maxWidth = spacing.railWidth, minHeight = spacing.touchTargetMin)
            .drawBehind {
                drawOutline(outline = shape.createOutline(size, layoutDirection, this), color = lerp(restingFill, activeFill, active.value))
            }
            .then(if (focused) Modifier.border(spacing.focusRingWidth, colors.primary, shape) else Modifier)
            .clickable(interactionSource = interactionSource, indication = null, role = Role.Tab) { onSelect(item.id) }
            .testTag("nav.item.${item.id}")
            .semantics {
                contentDescription = item.label
                this.selected = selected
            }
            .padding(vertical = spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.navItemGap),
    ) {
        Icon(
            imageVector = TembraIcons.vector(item.icon),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(spacing.navRailIconSize),
        )
        Text(
            text = item.label,
            style = TembraTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else null,
            color = content,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
