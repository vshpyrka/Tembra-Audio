package audio.tembra.ui.navigation

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import audio.tembra.ui.platform.isIphoneDuo

/**
 * The layout mode of the real window (research R3). `currentWindowAdaptiveInfoV2()` is state-backed, so this recomputes
 * on every rotation, fold, Split View or desktop-mode resize (Constitution IV).
 */
@Composable
fun rememberLayoutMode(): LayoutMode =
    LayoutMode.from(currentWindowAdaptiveInfoV2().windowSizeClass, isIphoneDuo)
