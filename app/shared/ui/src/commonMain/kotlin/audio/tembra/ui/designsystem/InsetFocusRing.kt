package audio.tembra.ui.designsystem

import androidx.compose.material3.RippleThemeConfiguration

/**
 * Material's inset focus ring for its own components: keyboard focus strokes a ring inside the focused item's
 * indicator instead of Material's default faint state layer, so the icon-only navigation items keep a visible focus
 * indicator (Constitution IV, spec FR-014). Android's material3 1.5.0-beta01 and the JetBrains 1.12.0-alpha03 port name
 * Material's preset differently, so each platform hands over its own.
 */
internal expect val InsetFocusRing: RippleThemeConfiguration
