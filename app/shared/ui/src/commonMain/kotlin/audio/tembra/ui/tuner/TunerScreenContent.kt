package audio.tembra.ui.tuner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import audio.tembra.core.tuner.TunerMode
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.theme.WindowWidth

/**
 * The stateless tuner screen the UI tests drive (contracts/tuner-screen.md): the selector bar, then the Auto Listen
 * card or the By Ear card filling the remaining height. Root tag `screen.tuner` keeps the shell's convention.
 */
@Composable
fun TunerScreenContent(
    state: TunerUiState,
    callbacks: TunerCallbacks,
    telemetry: PitchTelemetry,
    windowWidth: WindowWidth,
    modifier: Modifier = Modifier,
) {
    val spacing = TembraTheme.spacing
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = spacing.pageMargin, vertical = spacing.sm)
            .testTag("screen.tuner"),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        TunerSelectors(state = state, callbacks = callbacks, windowWidth = windowWidth)
        when (state.mode) {
            TunerMode.AutoListen -> AutoListenPane(
                state = state,
                telemetry = telemetry,
                windowWidth = windowWidth,
                onRequestMicrophone = callbacks.onRequestMicrophone,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            TunerMode.ByEar -> ByEarPane(
                state = state,
                callbacks = callbacks,
                windowWidth = windowWidth,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }
}
