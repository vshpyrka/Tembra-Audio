package audio.tembra.ui.designsystem

import androidx.compose.foundation.interaction.MutableInteractionSource
import audio.tembra.ui.theme.TembraIcon

/**
 * One row of a [TembraMenu]: its [id] (what `onSelect` reports and the row's `<menuTag>.<id>` test tag), its text,
 * an optional leading glyph, whether it can be chosen (an unavailable action stays listed, greyed and disabled), and an
 * [interactionSource] tests inject to drive hover and focus (`compose-ui-testing-patterns`).
 */
data class MenuItemSpec(
    val id: String,
    val label: String,
    val icon: TembraIcon? = null,
    val enabled: Boolean = true,
    val interactionSource: MutableInteractionSource? = null,
)
