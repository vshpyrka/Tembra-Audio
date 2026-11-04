package audio.tembra.ui.platform

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize

/**
 * Gives [content] the size of the activity's window as laid out, so `currentWindowAdaptiveInfoV2()` follows a rotation
 * that MainActivity handles in place (`configChanges`). Compose reads the window metrics while the configuration change
 * is dispatched, and on some devices the window is not resized yet and the value is never read again: a Redmi Note 5
 * (API 28) reported 1080 x 2030 in a 2160 x 1080 window and an OPPO CPH2239 (API 30) 720 x 1600 in 1528 x 720, so every
 * adaptive layout stayed one rotation behind (2026-10-15); the Pixels on API 37 report the new size. The decor view's
 * size is used once it has been laid out under the current configuration; until then the platform's value stands.
 */
@Composable
fun ProvideLaidOutWindowSize(content: @Composable () -> Unit) {
    val decorView = LocalView.current.rootView
    val laidOut = remember(decorView) { mutableStateOf<LaidOutWindow?>(null) }
    DisposableEffect(decorView) {
        val listener = View.OnLayoutChangeListener { view, left, top, right, bottom, _, _, _, _ ->
            laidOut.value = LaidOutWindow(IntSize(right - left, bottom - top), Configuration(view.resources.configuration))
        }
        decorView.addOnLayoutChangeListener(listener)
        onDispose { decorView.removeOnLayoutChangeListener(listener) }
    }
    val platform = LocalWindowInfo.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val windowInfo = remember(platform, density, laidOut, configuration) {
        LaidOutWindowInfo(platform, density, laidOut, configuration)
    }
    CompositionLocalProvider(LocalWindowInfo provides windowInfo, content = content)
}

/** A layout pass of the decor view and the configuration it ran under. */
private class LaidOutWindow(val size: IntSize, val configuration: Configuration)

private class LaidOutWindowInfo(
    private val platform: WindowInfo,
    private val density: Density,
    private val laidOut: State<LaidOutWindow?>,
    private val current: Configuration,
) : WindowInfo by platform {
    override val containerSize: IntSize
        get() = laidOut.value?.takeIf { it.isCurrent() }?.size ?: platform.containerSize

    override val containerDpSize: DpSize
        get() = with(density) { containerSize.toSize().toDpSize() }

    // A layout from before the latest rotation or resize describes the previous window.
    private fun LaidOutWindow.isCurrent(): Boolean =
        size != IntSize.Zero && (current.diff(configuration) and WINDOW_SIZE_CHANGES) == 0

    private companion object {
        const val WINDOW_SIZE_CHANGES = ActivityInfo.CONFIG_ORIENTATION or ActivityInfo.CONFIG_SCREEN_SIZE
    }
}
