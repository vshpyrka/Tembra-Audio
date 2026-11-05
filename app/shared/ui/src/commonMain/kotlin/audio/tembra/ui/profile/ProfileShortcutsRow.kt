package audio.tembra.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import audio.tembra.ui.designsystem.drawFocusRing
import audio.tembra.ui.designsystem.focusIdentity
import audio.tembra.ui.designsystem.hoverArea
import audio.tembra.ui.shortcuts.ShortcutCopy
import audio.tembra.ui.theme.TembraShapes
import audio.tembra.ui.theme.TembraTheme

/**
 * Profile's "Keyboard shortcuts" row (feature 011, spec FR-022): opens the shortcut sheet, so the list is reachable
 * without a keyboard too. A card-coloured row with the hover tint and the focus ring of the design system's controls.
 */
@Composable
internal fun ProfileShortcutsRow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = TembraTheme.spacing
    val colors = TembraTheme.colors
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    val ringColor = colors.primary
    val ring = spacing.focusRingWidth
    Row(
        modifier = modifier
            .widthIn(max = spacing.shortcutSheetMaxWidth)
            .heightIn(min = spacing.touchTargetMin)
            .clip(RoundedCornerShape(TembraShapes.large))
            .background(TembraTheme.daw.surfaceLevel1)
            .hoverArea(hovered, TembraShapes.large)
            .drawWithContent {
                drawContent()
                if (focused) drawFocusRing(ringColor, ring.toPx(), CornerRadius(TembraShapes.large.toPx()))
            }
            .focusIdentity(PROFILE_SHORTCUTS_TAG)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .testTag(PROFILE_SHORTCUTS_TAG)
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(ShortcutCopy.profileRow, style = TembraTheme.typography.titleMedium, color = colors.onSurface)
            Text(ShortcutCopy.profileRowDetail, style = TembraTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
    }
}

internal const val PROFILE_SHORTCUTS_TAG = "profile.shortcuts"
