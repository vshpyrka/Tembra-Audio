package audio.tembra.ui.perf

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import audio.tembra.engine.AudioEngine
import audio.tembra.engine.CaptureSnapshot
import audio.tembra.ui.theme.TembraTheme

/**
 * The measurement build's one developer control (feature 007, US5): writes the engine's audio-health counters to
 * `engine-health.json` through [writer]; the stress journey taps it right after its playback phase on both platforms
 * (anchor `devtools.writeHealth`), while the transport still owns the device. Composed only when `PerfBuild.PERF_ENABLED`, so release builds carry none of it.
 * A dot no larger than the smallest page margin (8 dp): the app places it over the screens' top end corner, which
 * the shell's top bar used to cover, so it must never sit over a control the journeys tap.
 */
@Composable
fun AudioHealthWriteButton(
    engine: AudioEngine,
    modifier: Modifier = Modifier,
    writer: EngineHealthWriter = rememberEngineHealthWriter(),
) {
    // The caller's alignment and inset padding stay on an outer box, and the dot's touch bounds are its own 8 dp:
    // Compose grows every clickable's touch bounds to the view configuration's 48 dp minimum and Android accessibility
    // reports those bounds, so a journey's click at the centre of the node (clipped at the screen edge) landed on the
    // Timeline's overflow button beneath and opened its menu (seen three times on the Pixel 6 Pro, feature 009). A
    // developer control, not a user's.
    val configuration = LocalViewConfiguration.current
    val exactTouchBounds = remember(configuration) {
        object : ViewConfiguration by configuration {
            override val minimumTouchTargetSize: DpSize get() = DpSize.Zero
        }
    }
    Box(modifier) {
        CompositionLocalProvider(LocalViewConfiguration provides exactTouchBounds) {
            Box(
                modifier = Modifier
                    .size(TembraTheme.spacing.sm)
                    .clip(CircleShape)
                    .background(TembraTheme.colors.onSurfaceVariant)
                    .pointerHoverIcon(PointerIcon.Hand).clickable(role = Role.Button) { writer.write(engineHealthJson(engine.audioHealth(), CaptureSnapshot().also(engine::readCapture))) }
                    .testTag(PerfAnchors.DEVTOOLS_WRITE_HEALTH)
                    .semantics { contentDescription = "Write engine health" },
            )
        }
    }
}
