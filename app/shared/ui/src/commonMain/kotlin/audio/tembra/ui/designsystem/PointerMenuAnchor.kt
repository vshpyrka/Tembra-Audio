package audio.tembra.ui.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset

/**
 * Where an item's menu anchor (its ⋮ button) sits inside the item, so a secondary click anywhere on the item drops the
 * anchor's [TembraMenu] at the pointer (feature 011, spec FR-012). Mark the item with [pointerMenuItem] and the anchor
 * with [pointerMenuAnchor]; [offsetFor] turns a position local to the item (as `onSecondaryClick` reports it) into the
 * menu's offset. Layout coordinates only: nothing here is composition state.
 */
internal class PointerMenuAnchor {
    internal var item: LayoutCoordinates? = null
    internal var anchor: LayoutCoordinates? = null

    fun offsetFor(pointerInItem: Offset, density: Density): DpOffset {
        val item = item?.takeIf { it.isAttached } ?: return DpOffset.Zero
        val anchor = anchor?.takeIf { it.isAttached } ?: return DpOffset.Zero
        val anchorInItem = item.localPositionOf(anchor, Offset.Zero)
        return density.menuOffsetAt(pointerInItem - anchorInItem, anchor.size.height)
    }
}

@Composable
internal fun rememberPointerMenuAnchor(): PointerMenuAnchor = remember { PointerMenuAnchor() }

internal fun Modifier.pointerMenuItem(anchor: PointerMenuAnchor): Modifier = onGloballyPositioned { anchor.item = it }

internal fun Modifier.pointerMenuAnchor(anchor: PointerMenuAnchor): Modifier = onGloballyPositioned { anchor.anchor = it }
