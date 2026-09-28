package audio.tembra.ui.designsystem

import androidx.compose.foundation.interaction.MutableInteractionSource
import audio.tembra.ui.theme.TembraIcon

/**
 * What a bar or rail item needs to know; the chrome components never see the destination registry. An
 * [interactionSource] lets a caller drive or observe the item's hover, press and focus state; null keeps one private.
 */
data class NavigationItemSpec(
    val id: String,
    val label: String,
    val icon: TembraIcon,
    val interactionSource: MutableInteractionSource? = null,
)
