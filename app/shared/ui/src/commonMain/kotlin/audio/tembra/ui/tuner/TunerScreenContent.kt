package audio.tembra.ui.tuner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import audio.tembra.ui.designsystem.FoldSplit
import audio.tembra.ui.designsystem.FoldSplitRow
import audio.tembra.ui.posture.TabletopFold
import audio.tembra.ui.posture.VerticalFold
import audio.tembra.ui.theme.TembraTheme

/**
 * The stateless tuner screen the UI tests drive (contracts/tuner-screen.md). [TunerLayout.Stacked] and [TunerLayout.Wide]
 * put the selector bar above the Auto Listen or By Ear card; [TunerLayout.Landscape] puts the settings column beside
 * it (spec FR-023); [TunerLayout.Tabletop] splits the screen at the tabletop [fold]: the Auto Listen or By Ear card above
 * it, [TunerTabletopSettings] below it (feature 005, FR-003, FR-004; a null [fold] splits in half, as the UI tests do),
 * the regions tagged `tuner.tabletop.upper` and `.lower`; [TunerLayout.Wide] with a book-posture [verticalFold] puts
 * the same controls card before the hinge and the gauge or the string cards after it (design iteration D3). Root tag `screen.tuner` keeps the shell's convention. Each
 * child receives only the fields it renders, so the 10 Hz reading recomposes the meter and nothing beside it. The noise
 * gate control (feature 006, FR-016) is built once here, while [TunerUiState.showNoiseGate], and handed to the place
 * the layout gives it: the foot of the Auto Listen card in the stacked and wide layouts.
 */
@Composable
fun TunerScreenContent(
    state: TunerUiState,
    callbacks: TunerCallbacks,
    telemetry: PitchTelemetry,
    layout: TunerLayout,
    modifier: Modifier = Modifier,
    fold: TabletopFold? = null,
    verticalFold: VerticalFold? = null,
) {
    val spacing = TembraTheme.spacing
    val root = modifier
        .fillMaxSize()
        .padding(horizontal = spacing.pageMargin, vertical = spacing.sm)
        .testTag("screen.tuner")
    val noiseGate: (@Composable () -> Unit)? = if (state.showNoiseGate) {
        {
            NoiseGateSlider(
                gate = state.noiseGate,
                onGate = callbacks.onNoiseGate,
                onGateSettled = callbacks.onNoiseGateSettled,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    } else {
        null
    }
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
        TunerLayout.Tabletop -> FoldSplit(
            fold = fold,
            modifier = root,
            upper = {
                Box(modifier = Modifier.fillMaxSize().testTag("tuner.tabletop.upper")) {
                    ModePane(state = state, callbacks = callbacks, telemetry = telemetry, layout = layout, modifier = Modifier.fillMaxSize())
                }
            },
            lower = {
                Box(modifier = Modifier.fillMaxSize().testTag("tuner.tabletop.lower")) {
                    TunerTabletopSettings(
                        instrument = state.instrument,
                        guitarStrings = state.guitarStrings,
                        mode = state.mode,
                        pill = state.pill,
                        preset = state.preset,
                        presets = state.presets,
                        loopTone = state.loopTone,
                        callbacks = callbacks,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            },
        )
        // A book-posture foldable splits the wide tuner at its hinge (design iteration D3): the controls card before it,
        // the gauge or the string cards after it, each a gutter clear of the fold.
        TunerLayout.Wide if verticalFold != null -> FoldSplitRow(
            fold = verticalFold,
            modifier = root,
            start = {
                TunerTabletopSettings(
                    instrument = state.instrument,
                    guitarStrings = state.guitarStrings,
                    mode = state.mode,
                    pill = state.pill,
                    preset = state.preset,
                    presets = state.presets,
                    loopTone = state.loopTone,
                    callbacks = callbacks,
                    modifier = Modifier.fillMaxSize(),
                )
            },
            end = {
                when (state.mode) {
                    TunerMode.AutoListen -> ModePane(state = state, callbacks = callbacks, telemetry = telemetry, layout = layout, modifier = Modifier.fillMaxSize())
                    TunerMode.ByEar -> ByEarCardsPane(
                        preset = state.preset,
                        playingIndex = state.audition?.stringIndex,
                        callbacks = callbacks,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            },
        )
        TunerLayout.Stacked, TunerLayout.Wide -> Column(modifier = root, verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            TunerSelectors(
                instrument = state.instrument,
                mode = state.mode,
                pill = state.pill,
                callbacks = callbacks,
                layout = layout,
            )
            ModePane(
                state = state,
                callbacks = callbacks,
                telemetry = telemetry,
                layout = layout,
                modifier = Modifier.fillMaxWidth().weight(1f),
                autoListenFooter = noiseGate,
            )
        }
    }
}

/** The Auto Listen card or the By Ear card for the current mode; [autoListenFooter] goes to the foot of the Auto Listen card. */
@Composable
private fun ModePane(
    state: TunerUiState,
    callbacks: TunerCallbacks,
    telemetry: PitchTelemetry,
    layout: TunerLayout,
    modifier: Modifier,
    autoListenFooter: (@Composable () -> Unit)? = null,
) {
    when (state.mode) {
        TunerMode.AutoListen -> AutoListenPane(
            reading = state.reading,
            listening = state.audio == AudioState.Listening,
            showDeniedState = state.showDeniedState,
            telemetry = telemetry,
            layout = layout,
            onRequestMicrophone = callbacks.onRequestMicrophone,
            modifier = modifier,
            footer = autoListenFooter,
        )
        TunerMode.ByEar -> ByEarPane(
            guitarStrings = state.guitarStrings.takeIf { state.instrument.hasStringCount },
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
