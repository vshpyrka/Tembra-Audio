package audio.tembra.ui.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import audio.tembra.ui.theme.TembraIcons
import audio.tembra.ui.theme.TembraTheme

/**
 * The compact-width navigation bar (spec FR-003, FR-004, FR-019): 64 dp plus the bottom inset on
 * surfaceContainerLowest, six equal cells so every labelled item fits from 320 dp up, each item at least 56 x 44 dp
 * with the glyph above a labelSmall label. The active item's capsule is drawn behind the content, 4 dp wider than the
 * label on each side so the rounded ends never cut the glyphs even when a label fills its cell (six items at 360 dp
 * leave "Metronome" no spare width), in primaryContainer with onPrimaryContainer content and the design's clip glow;
 * the fill fades between items over the motion token. Hover or a press raises an inactive item to onSurface, keyboard
 * focus draws a primary ring, and every item is a tab with its label as description (spec FR-014, FR-015). Labels may
 * wrap onto a second line under large fonts and the bar grows with them (spec edge cases).
 */
@Composable
fun TembraNavigationBar(
    items: List<NavigationItemSpec>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = TembraTheme.spacing
    Surface(
        modifier = modifier.fillMaxWidth().testTag("chrome.bottomBar"),
        color = TembraTheme.colors.surfaceContainerLowest,
    ) {
        Row(
            modifier = Modifier
                // The sides as the content clears them: the iPhone Duo's closed screen keeps its status column at the end edge.
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)))
                .fillMaxWidth()
                .heightIn(min = spacing.bottomBarHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (item in items) {
                NavigationBarItem(
                    item = item,
                    selected = item.id == selectedId,
                    onSelect = onSelect,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NavigationBarItem(
    item: NavigationItemSpec,
    selected: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = TembraTheme.spacing
    val colors = TembraTheme.colors
    val motion = TembraTheme.motion
    val interactionSource = item.interactionSource ?: remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    // Read in composition only for the content colour, which Icon and Text take as a value; the fill reads it in draw.
    val active = animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(motion.effectiveDuration, easing = motion.standardEasing),
        label = "navBarItemActive",
    )
    val restingContent = if (hovered || pressed) colors.onSurface else colors.onSurfaceVariant
    val content = lerp(restingContent, colors.onPrimaryContainer, active.value)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .sizeIn(minWidth = spacing.navItemMinWidth, minHeight = spacing.touchTargetMin)
                .activePill(
                    fill = colors.primaryContainer,
                    glow = TembraTheme.daw.clipGlow,
                    glowBlur = TembraTheme.daw.clipGlowBlur,
                    strength = active,
                    horizontalOverhang = spacing.xs,
                    focusRing = if (focused) colors.primary else null,
                    focusRingWidth = spacing.focusRingWidth,
                )
                .clickable(interactionSource = interactionSource, indication = null, role = Role.Tab) { onSelect(item.id) }
                .testTag("nav.item.${item.id}")
                .semantics {
                    contentDescription = item.label
                    this.selected = selected
                }
                .padding(vertical = spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.navItemGap),
        ) {
            Icon(
                imageVector = TembraIcons.vector(item.icon),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(spacing.navBarIconSize),
            )
            Text(
                text = item.label,
                style = TembraTheme.typography.labelSmall,
                color = content,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The active capsule and the design's `clip_glow` (0 0 12px), drawn behind the content and [horizontalOverhang] wider
 * than it on each side; the glow is layered capsules, never a shadow. [strength] is the selection animation, read in
 * the draw phase so the fade invalidates only drawing. The keyboard [focusRing] is stroked just inside the capsule's
 * edge. Nothing is clipped, so glyphs stay whole.
 */
private fun Modifier.activePill(
    fill: Color,
    glow: Color,
    glowBlur: Dp,
    strength: State<Float>,
    horizontalOverhang: Dp,
    focusRing: Color?,
    focusRingWidth: Dp,
): Modifier = drawBehind {
    val overhang = horizontalOverhang.toPx()
    val pillTopLeft = Offset(-overhang, 0f)
    val pillSize = Size(size.width + overhang * 2, size.height)
    val radius = CornerRadius(pillSize.height / 2)
    val active = strength.value
    if (active > 0f) {
        val layers = 8
        val blurPx = glowBlur.toPx()
        val layerAlpha = glow.alpha * active / layers
        for (layer in 1..layers) {
            val spread = blurPx * layer / layers
            drawRoundRect(
                color = glow.copy(alpha = layerAlpha),
                topLeft = Offset(pillTopLeft.x - spread, pillTopLeft.y - spread),
                size = Size(pillSize.width + spread * 2, pillSize.height + spread * 2),
                cornerRadius = CornerRadius(radius.x + spread),
            )
        }
        drawRoundRect(color = lerp(Color.Transparent, fill, active), topLeft = pillTopLeft, size = pillSize, cornerRadius = radius)
    }
    if (focusRing != null) {
        val ring = focusRingWidth.toPx()
        drawRoundRect(
            color = focusRing,
            topLeft = Offset(pillTopLeft.x + ring / 2, pillTopLeft.y + ring / 2),
            size = Size(pillSize.width - ring, pillSize.height - ring),
            cornerRadius = CornerRadius(radius.x - ring / 2),
            style = Stroke(width = ring),
        )
    }
}
