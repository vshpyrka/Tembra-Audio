package audio.tembra.ui.perf

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import audio.tembra.engine.AudioEngine
import audio.tembra.ui.theme.TembraTheme

/**
 * The measurement build's one developer control (feature 007, US5): writes the engine's audio-health counters to
 * `engine-health.json` through [writer]; the stress journey taps it last on both platforms (anchor
 * `devtools.writeHealth`). Composed only when `PerfBuild.PERF_ENABLED`, so release builds carry none of it.
 * A dot no larger than the smallest page margin (8 dp): the app places it over the screens' top end corner, which
 * the shell's top bar used to cover, so it must never sit over a control the journeys tap.
 */
@Composable
fun AudioHealthWriteButton(
    engine: AudioEngine,
    modifier: Modifier = Modifier,
    writer: EngineHealthWriter = rememberEngineHealthWriter(),
) {
    Box(
        modifier = modifier
            .size(TembraTheme.spacing.sm)
            .clip(CircleShape)
            .background(TembraTheme.colors.onSurfaceVariant)
            .clickable(role = Role.Button) { writer.write(engineHealthJson(engine.audioHealth())) }
            .testTag(PerfAnchors.DEVTOOLS_WRITE_HEALTH)
            .semantics { contentDescription = "Write engine health" },
    )
}
