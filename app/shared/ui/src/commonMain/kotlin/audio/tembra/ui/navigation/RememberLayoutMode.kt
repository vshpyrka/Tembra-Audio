package audio.tembra.ui.navigation

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable

/**
 * The layout mode of the real window (research R3). `currentWindowAdaptiveInfo()` is state-backed, so this recomputes
 * on every rotation, fold, Split View or desktop-mode resize (Constitution IV).
 */
@Composable
fun rememberLayoutMode(): LayoutMode =
    LayoutMode.from(currentWindowAdaptiveInfo(supportLargeAndXLargeWidth = false).windowSizeClass)
