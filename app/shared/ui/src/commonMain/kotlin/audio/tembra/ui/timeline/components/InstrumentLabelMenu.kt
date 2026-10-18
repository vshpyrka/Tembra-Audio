package audio.tembra.ui.timeline.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import audio.tembra.core.project.InstrumentLabel
import audio.tembra.ui.designsystem.MenuItemSpec
import audio.tembra.ui.designsystem.TembraMenu

/** The twelve instrument labels and "None" (spec FR-021) on [TembraMenu]; [anchor] is the control that opens it. */
@Composable
internal fun InstrumentLabelMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onSelect: (InstrumentLabel?) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = LABEL_MENU_TAG,
    anchor: @Composable () -> Unit,
) {
    TembraMenu(
        expanded = expanded,
        onDismiss = onDismiss,
        items = listOf(MenuItemSpec(LABEL_NONE, LABEL_NONE_TEXT)) + InstrumentLabel.entries.map { MenuItemSpec(it.serialName, it.displayName, InstrumentGlyphs.of(it)) },
        onSelect = { id -> onSelect(if (id == LABEL_NONE) null else InstrumentLabel.fromSerialName(id)) },
        modifier = modifier,
        testTag = testTag,
        anchor = anchor,
    )
}

internal const val LABEL_MENU_TAG = "timeline.labelMenu"
internal const val LABEL_NONE = "none"
internal const val LABEL_NONE_TEXT = "None"
