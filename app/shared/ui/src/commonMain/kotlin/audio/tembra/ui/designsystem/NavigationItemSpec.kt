package audio.tembra.ui.designsystem

import audio.tembra.ui.theme.TembraIcon

/** What a bar or rail item needs to know; the chrome components never see the destination registry. */
data class NavigationItemSpec(val id: String, val label: String, val icon: TembraIcon)
