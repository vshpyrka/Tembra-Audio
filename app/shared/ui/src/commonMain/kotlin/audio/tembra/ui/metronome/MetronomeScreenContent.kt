package audio.tembra.ui.metronome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import audio.tembra.core.metronome.AccentPattern
import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TempoMarking
import audio.tembra.ui.designsystem.CardDiscs
import audio.tembra.ui.designsystem.TembraGradientCard
import audio.tembra.ui.metronome.components.AccentCells
import audio.tembra.ui.metronome.components.PlayPauseButton
import audio.tembra.ui.metronome.components.TapTempoButton
import audio.tembra.ui.metronome.components.TempoDial
import audio.tembra.ui.metronome.components.TempoSlider
import audio.tembra.ui.theme.TembraShapes
import audio.tembra.ui.theme.TembraTheme

/**
 * The stateless metronome screen the UI tests drive (contracts/metronome-screen.md). The Stacked composition: a
 * scrolling column with the beat cells card and the dial card, and the tap and play row pinned below it so the
 * transport stays reachable. The settings panel (signature, subdivision, style, sound) joins the column with
 * User Stories 2 and 4; [MetronomeLayout.Wide] and [MetronomeLayout.Landscape] get their own forms with User Story 7
 * and use this one until then. Root tag `screen.metronome` keeps the shell's convention; snackbars show over it.
 */
@Composable
fun MetronomeScreenContent(
    state: MetronomeUiState,
    callbacks: MetronomeCallbacks,
    telemetry: BeatTelemetry,
    layout: MetronomeLayout,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val spacing = TembraTheme.spacing
    Box(modifier = modifier.fillMaxSize()) {
        val content = Modifier
            .fillMaxSize()
            .padding(horizontal = spacing.pageMargin, vertical = spacing.sm)
            .testTag("screen.metronome")
        when (layout) {
            MetronomeLayout.Stacked,
            MetronomeLayout.Wide,
            MetronomeLayout.Landscape,
            -> StackedMetronome(state = state, callbacks = callbacks, telemetry = telemetry, modifier = content)
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun StackedMetronome(state: MetronomeUiState, callbacks: MetronomeCallbacks, telemetry: BeatTelemetry, modifier: Modifier) {
    val spacing = TembraTheme.spacing
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            BeatCellsCard(pattern = state.settings.pattern, running = state.running, telemetry = telemetry, onCycle = callbacks.onCycleBeat)
            DialCard(
                tempo = state.settings.tempo,
                marking = state.marking,
                canStepDown = state.canStepDown,
                canStepUp = state.canStepUp,
                onTempo = callbacks.onTempo,
                onStep = callbacks.onStep,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TapTempoButton(caption = MetronomeCopy.caption(state.tapCaption), onTap = callbacks.onTap, modifier = Modifier.weight(1f))
            PlayPauseButton(running = state.running, onToggle = callbacks.onTogglePlayback)
        }
    }
}

/** The section card of the beat cells: `surfaceContainerLow`, 12 dp corners, 8 dp padding. */
@Composable
private fun BeatCellsCard(pattern: AccentPattern, running: Boolean, telemetry: BeatTelemetry, onCycle: (Int) -> Unit) {
    val spacing = TembraTheme.spacing
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(TembraTheme.colors.surfaceContainerLow, RoundedCornerShape(TembraShapes.medium))
            .padding(spacing.sm),
    ) {
        AccentCells(pattern = pattern, running = running, telemetry = telemetry, onCycle = onCycle)
    }
}

/** The dial card: the `tempoDialCard` glow with the `tempoDialOverlay` tint, the dial (capped at 270 dp) and the slider. */
@Composable
private fun DialCard(
    tempo: Tempo,
    marking: TempoMarking,
    canStepDown: Boolean,
    canStepUp: Boolean,
    onTempo: (Tempo) -> Unit,
    onStep: (Int) -> Unit,
) {
    val spacing = TembraTheme.spacing
    val gradients = TembraTheme.gradients
    TembraGradientCard(
        glow = gradients.tempoDialCard,
        modifier = Modifier.fillMaxWidth(),
        discs = CardDiscs.None,
        overlay = gradients.tempoDialOverlay,
        testTag = "metronome.dialCard",
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            TempoDial(
                tempo = tempo,
                marking = marking,
                canStepDown = canStepDown,
                canStepUp = canStepUp,
                onTempo = onTempo,
                onStep = onStep,
                modifier = Modifier.widthIn(max = spacing.tempoDialMaxWidth).fillMaxWidth(),
            )
            TempoSlider(tempo = tempo, onTempo = onTempo, modifier = Modifier.fillMaxWidth())
        }
    }
}
