package audio.tembra.ui.perf

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import audio.tembra.engine.AudioEngine
import audio.tembra.ui.theme.TembraIcon
import audio.tembra.ui.theme.TembraIcons
import audio.tembra.ui.theme.TembraTheme

/**
 * The measurement build's one developer control (feature 007, US5): writes the engine's audio-health counters to
 * `engine-health.json` through [writer]; the stress journey taps it last on both platforms (anchor
 * `devtools.writeHealth`). Composed only when `PerfBuild.PERF_ENABLED`, so release builds carry none of it.
 */
@Composable
fun AudioHealthWriteButton(
    engine: AudioEngine,
    modifier: Modifier = Modifier,
    writer: EngineHealthWriter = rememberEngineHealthWriter(),
) {
    IconButton(
        onClick = { writer.write(engineHealthJson(engine.audioHealth())) },
        modifier = modifier
            .testTag(PerfAnchors.DEVTOOLS_WRITE_HEALTH)
            .semantics { contentDescription = "Write engine health" },
    ) {
        Icon(imageVector = TembraIcons.vector(TembraIcon.Timeline), contentDescription = null, tint = TembraTheme.colors.onSurfaceVariant)
    }
}
