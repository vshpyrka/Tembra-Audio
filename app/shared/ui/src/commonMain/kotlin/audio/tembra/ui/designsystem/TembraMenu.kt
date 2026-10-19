package audio.tembra.ui.designsystem

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import audio.tembra.ui.theme.TembraIcons
import audio.tembra.ui.theme.TembraShapes
import audio.tembra.ui.theme.TembraTheme

/**
 * The app's dropdown (contracts/design-system-additions.md, feature 009): [anchor] is the caller's control that opens
 * it (the slot is rendered in place, so the menu drops from the anchor); while [expanded] the [items] are listed on
 * `surfaceContainerHighest` as 44 dp rows whose hover and focus tint the row (`surfaceContainerHigh` on hover, the
 * `primary` focus ring on keyboard focus); choosing a row calls [onSelect] with its id and the caller closes the
 * menu through [onDismiss]. Disabled rows stay listed in `onSurfaceVariant` at the disabled alpha and select nothing.
 * The menu carries [testTag] and each row `<testTag>.<id>`.
 */
@Composable
fun TembraMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    items: List<MenuItemSpec>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    anchor: @Composable () -> Unit,
) {
    val colors = TembraTheme.colors
    val spacing = TembraTheme.spacing
    Box(modifier = modifier) {
        anchor()
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
            modifier = Modifier.testTagIfSet(testTag).widthIn(min = spacing.soundMenuWidth).heightIn(max = MenuMaxHeight),
            shape = RoundedCornerShape(TembraShapes.medium),
            containerColor = colors.surfaceContainerHighest,
        ) {
            for (item in items) {
                TembraMenuRow(
                    item = item,
                    onClick = {
                        onDismiss()
                        onSelect(item.id)
                    },
                    modifier = Modifier.testTagIfSet(testTag?.let { "$it.${item.id}" }),
                )
            }
        }
    }
}

/** One 44 dp row; hover and focus come from the item's injected source or a remembered one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TembraMenuRow(item: MenuItemSpec, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = TembraTheme.colors
    val spacing = TembraTheme.spacing
    val source = item.interactionSource ?: remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    val content = when {
        !item.enabled -> colors.onSurfaceVariant.copy(alpha = DisabledAlpha)
        hovered || pressed -> colors.onSurface
        else -> colors.onSurfaceVariant
    }
    val fill = if ((hovered || pressed) && item.enabled) colors.surfaceContainerHigh else colors.surfaceContainerHighest
    val ring = spacing.focusRingWidth
    val ringColor = colors.primary
    // Material's ripple would lay its lighter hover overlay over the row's own tint; the row draws its states itself.
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
    DropdownMenuItem(
        text = {
            Text(
                text = item.label,
                style = TembraTheme.typography.labelMedium,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        onClick = onClick,
        modifier = modifier
            .heightIn(min = spacing.touchTargetMin)
            .drawBehind {
                drawRect(color = fill)
                if (focused) drawFocusRing(ringColor, ring.toPx(), CornerRadius(0f))
            },
        leadingIcon = item.icon?.let { icon ->
            {
                Icon(
                    imageVector = TembraIcons.vector(icon),
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(spacing.pickerIconSize),
                )
            }
        },
        enabled = item.enabled,
        interactionSource = source,
        contentPadding = PaddingValues(horizontal = spacing.md),
    )
    }
}

private val MenuMaxHeight = androidx.compose.ui.unit.Dp(360f)
private const val DisabledAlpha = 0.38f
