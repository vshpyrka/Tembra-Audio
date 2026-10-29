package audio.tembra.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import audio.tembra.engine.AudioState
import audio.tembra.engine.PitchSnapshot
import audio.tembra.ui.designsystem.DrawAhead
import audio.tembra.ui.metronome.MetronomeCallbacks
import audio.tembra.ui.metronome.MetronomeLayout
import audio.tembra.ui.metronome.MetronomeScreenContent
import audio.tembra.ui.metronome.MetronomeUiState
import audio.tembra.ui.platform.MicrophonePermissionStatus
import audio.tembra.ui.platform.isIphoneDuo
import audio.tembra.ui.posture.currentTabletopFold
import audio.tembra.ui.posture.currentVerticalFold
import audio.tembra.ui.tuner.MeterReading
import audio.tembra.ui.tuner.TunerCallbacks
import audio.tembra.ui.tuner.TunerLayout
import audio.tembra.ui.tuner.TunerScreenContent
import audio.tembra.ui.tuner.TunerUiState

/**
 * The Tuner and the Metronome drawn once ahead ([DrawAhead]) after launch, one sample after the other, with no
 * ViewModel, engine or callback behind them, so the first frames that show either screen find the GPU ready for their
 * shapes and glyphs: the Tuner listening with no note and with three notes (the meter reads them from the sample
 * telemetry, as it reads the engine's), and the default metronome, stopped.
 */
@Composable
internal fun DestinationsDrawnAhead(modifier: Modifier = Modifier) {
    var pending by remember { mutableStateOf(Sample.entries.toList()) }
    val next = pending.firstOrNull() ?: return
    key(next) {
        DrawAhead(onDrawn = { pending = pending.drop(1) }, modifier = modifier.fillMaxSize()) { constraints ->
            when (next) {
                Sample.TunerListening -> TunerDrawnAhead(constraints, hz = null)
                Sample.TunerSharp -> TunerDrawnAhead(constraints, hz = 112.4f)
                Sample.TunerInTune -> TunerDrawnAhead(constraints, hz = 138.59f)
                Sample.TunerFlat -> TunerDrawnAhead(constraints, hz = 365f)
                Sample.Metronome -> MetronomeDrawnAhead(constraints)
            }
        }
    }
}

/**
 * The samples: the Tuner resting, then A2 a little sharp, C♯3 in tune and F♯4 flat, which between them draw the meter's
 * large outlined note names with a letter, a sharp and digits, the in-tune and off-tune gauge and both needle sides; and
 * the Metronome.
 */
private enum class Sample { TunerListening, TunerSharp, TunerInTune, TunerFlat, Metronome }

/** The Tuner listening and hearing [hz], or nothing when null. */
@Composable
private fun TunerDrawnAhead(constraints: Constraints, hz: Float?) {
    val fold = currentTabletopFold()
    val height = with(LocalDensity.current) { constraints.maxHeight.toDp() }
    val heard = PitchSnapshot().apply {
        state = AudioState.Listening
        hasPitch = hz != null
        frequencyHz = hz ?: 0f
        clarity = 1f
    }
    TunerScreenContent(
        state = TunerUiState(
            loaded = true,
            microphone = MicrophonePermissionStatus.Granted,
            audio = AudioState.Listening,
            reading = MeterReading.from(heard),
        ),
        callbacks = TunerCallbacks.None,
        telemetry = { into -> into.copyFrom(heard) },
        layout = TunerLayout.from(currentWindowAdaptiveInfoV2().windowSizeClass, fold).forContentHeight(height),
        modifier = Modifier.fillMaxSize(),
        fold = fold,
        verticalFold = currentVerticalFold(),
    )
}

@Composable
private fun MetronomeDrawnAhead(constraints: Constraints) {
    val fold = currentTabletopFold()
    val height = with(LocalDensity.current) { constraints.maxHeight.toDp() }
    MetronomeScreenContent(
        state = MetronomeUiState(loaded = true),
        callbacks = MetronomeCallbacks.None,
        telemetry = { -1 },
        layout = MetronomeLayout.from(currentWindowAdaptiveInfoV2().windowSizeClass, fold, isIphoneDuo).forContentHeight(height),
        snackbarHostState = remember { SnackbarHostState() },
        modifier = Modifier.fillMaxSize(),
        fold = fold,
        verticalFold = currentVerticalFold(),
    )
}
