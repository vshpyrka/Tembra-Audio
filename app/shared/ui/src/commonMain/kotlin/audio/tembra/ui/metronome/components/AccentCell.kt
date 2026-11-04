package audio.tembra.ui.metronome.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import audio.tembra.core.metronome.BeatLevel
import audio.tembra.ui.designsystem.drawFocusRing
import audio.tembra.ui.designsystem.stepBasedFitFirst
import audio.tembra.ui.designsystem.testTagIfSet
import audio.tembra.ui.metronome.MetronomeCopy
import audio.tembra.ui.theme.TembraShapes
import audio.tembra.ui.theme.TembraTheme
import kotlin.math.max

/**
 * One beat of the bar (research R7, R10): a `surfaceContainerLowest` card with a 64 dp bar box whose bar height and
 * colour follow [level] (Accent `secondary` 95 % with the `accentGlow`, Click `primary` 62 %, Sub `primary` at 70 %
 * 42 %) and the level's name below. [lit] is a provider read only in the draw phase: while it answers true the bar box
 * is outlined in `secondary`, the bar is drawn at full alpha and the Accent glow brightens (not under reduce motion).
 * [playing] (the 10 Hz sample) adds ", playing" to the cell's state for accessibility. Hover and press raise the card to
 * `surfaceContainerLow`; keyboard focus draws the `primary` ring. [label] is drawn under the bar box, the level's name
 * by default ([AccentCellLabel]); a row of cells too narrow for their widest label passes null and the bar alone shows
 * the level (design iteration D1). [barHeight] is the bar box's height: 64 dp, or 32 dp in the landscape card's 44 dp
 * cells (T152). With a [testTag], the bar box carries `<tag>.bar`, the bar `<tag>.level` and the
 * drawn label `<tag>.label` (kept out of the accessibility tree: the state names the level).
 */
@Composable
internal fun AccentCell(
    index: Int,
    level: BeatLevel,
    lit: () -> Boolean,
    playing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null,
    testTag: String? = null,
    barHeight: Dp = TembraTheme.spacing.accentCellBarHeight,
    label: (@Composable () -> Unit)? = { AccentCellLabel(level) },
) {
    val colors = TembraTheme.colors
    val spacing = TembraTheme.spacing
    val glow = TembraTheme.gradients.accentGlow
    val flashGlow = !TembraTheme.motion.reduceMotion
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val cardColor = if (hovered || pressed) colors.surfaceContainerLow else colors.surfaceContainerLowest
    val barColor = when (level) {
        BeatLevel.Accent -> colors.secondary
        BeatLevel.Click -> colors.primary
        BeatLevel.Sub -> colors.primary.copy(alpha = MetronomeDefaults.SUB_BAR_ALPHA)
    }
    val fraction = when (level) {
        BeatLevel.Accent -> MetronomeDefaults.ACCENT_BAR_FRACTION
        BeatLevel.Click -> MetronomeDefaults.CLICK_BAR_FRACTION
        BeatLevel.Sub -> MetronomeDefaults.SUB_BAR_FRACTION
    }
    val outline = colors.secondary
    val ringColor = colors.primary
    val ring = spacing.focusRingWidth
    val cellShape = RoundedCornerShape(TembraShapes.default)

    Column(
        modifier = modifier
            .testTagIfSet(testTag)
            .clip(cellShape)
            .background(cardColor)
            .pointerHoverIcon(PointerIcon.Hand).clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = MetronomeCopy.beat(index)
                stateDescription = if (playing) "${level.label}, ${MetronomeCopy.playing}" else level.label
            }
            .drawWithContent {
                drawContent()
                if (focused) drawFocusRing(ringColor, ring.toPx(), CornerRadius(TembraShapes.default.toPx()))
            }
            .padding(MetronomeDefaults.CellPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MetronomeDefaults.CellPadding),
    ) {
        Box(
            modifier = Modifier
                .testTagIfSet(testTag?.let { "$it.bar" })
                .fillMaxWidth()
                .height(barHeight)
                .clip(RoundedCornerShape(MetronomeDefaults.BarBoxRadius))
                .background(colors.surfaceContainer)
                .drawWithContent {
                    drawContent()
                    if (lit()) {
                        val width = MetronomeDefaults.LitOutlineWidth.toPx()
                        drawRoundRect(
                            color = outline,
                            topLeft = Offset(width / 2, width / 2),
                            size = Size(size.width - width, size.height - width),
                            cornerRadius = CornerRadius(MetronomeDefaults.BarBoxRadius.toPx() - width / 2),
                            style = Stroke(width = width),
                        )
                    }
                }
                .padding(MetronomeDefaults.BarBoxPadding),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .testTagIfSet(testTag?.let { "$it.level" })
                    .fillMaxWidth()
                    .fillMaxHeight(fraction)
                    .drawBehind {
                        val on = lit()
                        if (level == BeatLevel.Accent) {
                            val strength = if (on && flashGlow) MetronomeDefaults.GLOW_LIT_BOOST else 1f
                            drawGlow(glow.color.copy(alpha = (glow.color.alpha * strength).coerceAtMost(1f)), glow.radius.toPx())
                        }
                        drawRoundRect(
                            color = if (on) barColor.copy(alpha = 1f) else barColor,
                            cornerRadius = CornerRadius(MetronomeDefaults.BarRadius.toPx()),
                        )
                    },
            )
        }
        if (label != null) {
            // The cell's state already names the level ("Beat 1", "Accent"): the drawn label is not read a second time.
            Box(modifier = Modifier.testTagIfSet(testTag?.let { "$it.label" }).clearAndSetSemantics {}) { label() }
        }
    }
}

/**
 * A cell's drawn level name: `microLabel` in the level's colour and weight (Accent `secondary` bold, Click `onSurface`
 * medium, Sub `onSurfaceVariant` regular), on one line. The row sizes cells from the label's measured width; should the
 * drawn face still come out wider (a font that loaded after that measure), the label steps its size down rather than
 * losing letters.
 */
@Composable
internal fun AccentCellLabel(level: BeatLevel, modifier: Modifier = Modifier) {
    val colors = TembraTheme.colors
    val style = TembraTheme.typographyExtras.microLabel
    Text(
        text = MetronomeCopy.levelLabel(level),
        style = style,
        fontWeight = when (level) {
            BeatLevel.Accent -> FontWeight.Bold
            BeatLevel.Click -> FontWeight.Medium
            BeatLevel.Sub -> FontWeight.Normal
        },
        color = when (level) {
            BeatLevel.Accent -> colors.secondary
            BeatLevel.Click -> colors.onSurface
            BeatLevel.Sub -> colors.onSurfaceVariant
        },
        maxLines = 1,
        overflow = TextOverflow.Clip,
        autoSize = stepBasedFitFirst(minFontSize = MetronomeDefaults.CellLabelMinSize, maxFontSize = style.fontSize),
        modifier = modifier,
    )
}

/** The bar's glow: a radial brush from its centre, solid over the bar and fading to nothing [reach] beyond its edges. */
private fun DrawScope.drawGlow(color: Color, reach: Float) {
    val radius = max(size.width, size.height) / 2 + reach
    val solid = (max(size.width, size.height) / 2) / radius
    drawRect(
        brush = Brush.radialGradient(0f to color, solid to color, 1f to Color.Transparent, center = center, radius = radius),
        topLeft = Offset(-reach, -reach),
        size = Size(size.width + reach * 2, size.height + reach * 2),
    )
}
