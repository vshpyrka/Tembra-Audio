package audio.tembra.ui.tuner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import audio.tembra.core.tuner.TunerMode
import audio.tembra.engine.AudioState
import audio.tembra.ui.theme.TembraTheme

/**
 * The stateless tuner screen the UI tests drive (contracts/tuner-screen.md). [TunerLayout.Stacked] and [TunerLayout.Wide]
 * put the selector bar above the Auto Listen or By Ear card; [TunerLayout.Landscape] puts the settings column beside
 * it (spec FR-023). Root tag `screen.tuner` keeps the shell's convention. Each child receives only the fields it
 * renders, so the 10 Hz reading recomposes the meter and nothing beside it.
 */
@Composable
fun TunerScreenContent(
    state: TunerUiState,
    callbacks: TunerCallbacks,
    telemetry: PitchTelemetry,
    layout: TunerLayout,
    modifier: Modifier = Modifier,
) {
    val spacing = TembraTheme.spacing
    val root = modifier
        .fillMaxSize()
        .padding(horizontal = spacing.pageMargin, vertical = spacing.sm)
        .testTag("screen.tuner")
    when (layout) {
        TunerLayout.Landscape -> Row(modifier = root, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            TunerSettingsColumn(
                instrument = state.instrument,
                guitarStrings = state.guitarStrings,
                mode = state.mode,
                pill = state.pill,
                preset = state.preset,
                presets = state.presets,
                loopTone = state.loopTone,
                callbacks = callbacks,
                modifier = Modifier.fillMaxHeight(),
            )
            ModePane(state = state, callbacks = callbacks, telemetry = telemetry, layout = layout, modifier = Modifier.weight(1f).fillMaxHeight())
        }
        TunerLayout.Stacked, TunerLayout.Wide -> Column(modifier = root, verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            TunerSelectors(
                instrument = state.instrument,
                guitarStrings = state.guitarStrings,
                mode = state.mode,
                pill = state.pill,
                callbacks = callbacks,
                layout = layout,
            )
            ModePane(state = state, callbacks = callbacks, telemetry = telemetry, layout = layout, modifier = Modifier.fillMaxWidth().weight(1f))
        }
    }
}

/** The Auto Listen card or the By Ear card for the current mode. */
@Composable
private fun ModePane(state: TunerUiState, callbacks: TunerCallbacks, telemetry: PitchTelemetry, layout: TunerLayout, modifier: Modifier) {
    when (state.mode) {
        TunerMode.AutoListen -> AutoListenPane(
            reading = state.reading,
            listening = state.audio == AudioState.Listening,
            showDeniedState = state.showDeniedState,
            telemetry = telemetry,
            layout = layout,
            onRequestMicrophone = callbacks.onRequestMicrophone,
            modifier = modifier,
        )
        TunerMode.ByEar -> ByEarPane(
            preset = state.preset,
            presets = state.presets,
            loopTone = state.loopTone,
            playingIndex = state.audition?.stringIndex,
            callbacks = callbacks,
            layout = layout,
            modifier = modifier,
        )
    }
}
