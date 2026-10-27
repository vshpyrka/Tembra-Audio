package audio.tembra.ui.transport

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import audio.tembra.ui.metronome.components.PlayPauseButton
import audio.tembra.ui.perf.PerfAnchors
import audio.tembra.ui.session.TransportTelemetry
import audio.tembra.ui.theme.TembraIcon
import audio.tembra.ui.theme.TembraTheme

/**
 * The transport bar (contracts/timeline-screen.md; the arranger tablet bar): on `surface_level1` with the
 * `surface_level2_border` seam above, [height] tall, the master volume at the start, skip-to-start, one bar back,
 * the metronome's play/pause button (tagged `transport.play`), one bar forward, the time readout and the metronome
 * toggle. Stateless: [state] and [callbacks] come from the owning screen, [telemetry] feeds the readout per frame.
 * The master volume follows the bar's own width (spec FR-007, D1 2026-10-26): from [MasterInlineFrom] the slider is
 * inline with its percentage ([MasterVolumeInline]); narrower it is a speaker button that opens the slider in a pop-up
 * above the bar ([MasterVolumeButton]). Narrower than [ReadoutStacksBelow] (a phone in portrait) the readout stacks
 * its two values so that every control keeps its target and nothing overlaps (spec FR-004 names all seven, left to
 * right).
 */
@Composable
internal fun TransportBar(
    state: TransportUiState,
    callbacks: TransportCallbacks,
    telemetry: TransportTelemetry,
    modifier: Modifier = Modifier,
    height: Dp = TembraTheme.spacing.transportHeightCompact,
) {
    val daw = TembraTheme.daw
    val spacing = TembraTheme.spacing
    val enabled = state.transportEnabled
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val masterInline = maxWidth >= MasterInlineFrom
        // A phone in portrait: tighter gaps and the stacked readout, so the seven controls fit.
        val narrow = maxWidth < ReadoutStacksBelow
        val sidePadding = if (narrow) spacing.xs else spacing.sm
        val gap = if (narrow) NarrowGap else spacing.xs
        val masterMin = if (masterInline) rememberMasterInlineMinWidth() else spacing.touchTargetMin
        // What is left for the readout once the master volume's floor and the five targets have theirs (a doubled font
        // shrinks to it). The readout carries no weight: it is measured at its own width.
        val readoutMax = maxWidth - sidePadding * 2 - gap * 6 - masterMin - spacing.touchTargetMin * 4 - PlayDiameter
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .background(daw.surfaceLevel1)
                .padding(horizontal = sidePadding)
                .testTag(ROOT_TAG),
            // The inline slider's weight takes up the room the controls leave; the speaker button cannot, so the phone
            // bar spreads that room evenly between the seven (the budget above keeps every gap at least [gap]).
            horizontalArrangement = if (masterInline) Arrangement.spacedBy(gap) else Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (masterInline) {
                MasterVolumeInline(
                    percent = state.masterVolume,
                    onVolume = callbacks.onMasterVolume,
                    modifier = Modifier.weight(1f, fill = false),
                )
            } else {
                MasterVolumeButton(
                    percent = state.masterVolume,
                    onVolume = callbacks.onMasterVolume,
                    // The button is centred in the bar: from its top to the bar's, then the card's own gap above the bar.
                    popupClearance = (height - spacing.touchTargetMin) / 2 + spacing.sm,
                )
            }
            TransportButton(
                icon = TembraIcon.SkipPrevious,
                description = TransportCopy.skipToStart,
                onClick = callbacks.onSkipToStart,
                enabled = enabled,
                testTag = PerfAnchors.TRANSPORT_SKIP_START,
            )
            TransportButton(
                icon = TembraIcon.FastRewind,
                description = TransportCopy.stepBack,
                onClick = { callbacks.onStepBar(-1) },
                enabled = enabled,
                testTag = STEP_BACK_TAG,
            )
            PlayPauseButton(
                running = state.isPlaying,
                onToggle = callbacks.onPlayPause,
                diameter = PlayDiameter,
                testTag = PerfAnchors.TRANSPORT_PLAY,
                startDescription = TransportCopy.play,
                stopDescription = TransportCopy.pause,
            )
            TransportButton(
                icon = TembraIcon.FastForward,
                description = TransportCopy.stepForward,
                onClick = { callbacks.onStepBar(1) },
                enabled = enabled,
                testTag = STEP_FORWARD_TAG,
            )
            TimeReadout(
                positionSeconds = state.positionSeconds,
                lengthSeconds = state.lengthSeconds,
                playing = state.isPlaying,
                telemetry = telemetry,
                stacked = narrow,
                maxWidth = readoutMax,
            )
            MetronomeToggle(
                on = state.metronomeOn,
                onToggle = callbacks.onMetronomeToggle,
                onSettings = callbacks.onMetronomeSettings,
                enabled = enabled,
            )
        }
    }
}

/** From this bar width the master slider is inline; narrower, a speaker button opens it in a pop-up (spec FR-007, D1). */
private val MasterInlineFrom = 600.dp
/** Below this width the one-line readout (about 150 dp) no longer fits beside the master volume and the five targets. */
private val ReadoutStacksBelow = 520.dp
/** The narrow bar's gap: 4 + 6 × 2 + 44 speaker + 3 × 44 + 48 + 44 + a 56 dp stacked readout fit 360 dp. */
private val NarrowGap = 2.dp

internal const val ROOT_TAG = "transport.bar"
internal const val STEP_BACK_TAG = "transport.stepBack"
internal const val STEP_FORWARD_TAG = "transport.stepForward"

/** The play button inside a 64 dp bar: 48 dp, the landscape metronome block's smallest size. */
private val PlayDiameter = 48.dp
