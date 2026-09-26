package audio.tembra.ui.tuner

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import audio.tembra.engine.AudioState
import audio.tembra.ui.designsystem.TembraGradientCard
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.theme.WindowWidth

/** The Auto Listen card: the meter centred on the design's radial glow, or the denied panel (contracts/tuner-screen.md). */
@Composable
internal fun AutoListenPane(
    state: TunerUiState,
    telemetry: PitchTelemetry,
    windowWidth: WindowWidth,
    onRequestMicrophone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = TembraTheme.spacing
    TembraGradientCard(glow = TembraTheme.gradients.meterCompact, testTag = "tuner.autoListen", modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.md, vertical = spacing.lg),
            contentAlignment = Alignment.Center,
        ) {
            if (state.showDeniedState) {
                MicrophoneDeniedState(onRequestMicrophone = onRequestMicrophone, modifier = Modifier.fillMaxWidth())
            } else {
                TunerMeter(
                    telemetry = telemetry,
                    reading = state.reading,
                    listening = state.audio == AudioState.Listening,
                    maxWidth = if (windowWidth == WindowWidth.Expanded) spacing.meterMaxWidthWide else spacing.meterMaxWidth,
                    testTag = "tuner.meter",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
