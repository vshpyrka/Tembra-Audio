package audio.tembra.ui.designsystem

import androidx.compose.foundation.interaction.MutableInteractionSource
import audio.tembra.ui.theme.TembraIcon

/**
 * One row of a [TembraMenu]: its [id] (what `onSelect` reports and the row's `<menuTag>.<id>` test tag), its text,
 * an optional leading glyph, whether it can be chosen (an unavailable action stays listed, greyed and disabled), an
 * [interactionSource] tests inject to drive hover and focus (`compose-ui-testing-patterns`), and [selected] for a
 * row that is one of a set of choices: true for the current one (checked, `<menuTag>.<id>.check`), false for the
 * others; null, the default, for an action row, which reads neither.
 */
data class MenuItemSpec(
    val id: String,
    val label: String,
    val icon: TembraIcon? = null,
    val enabled: Boolean = true,
    val interactionSource: MutableInteractionSource? = null,
    val selected: Boolean? = null,
)
