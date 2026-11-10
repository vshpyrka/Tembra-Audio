package audio.tembra.ui.mixer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import audio.tembra.engine.perf.PerfSection
import audio.tembra.engine.perf.PerfTrace
import audio.tembra.ui.designsystem.FocusMemory
import audio.tembra.ui.designsystem.focusRestoringRoot
import audio.tembra.ui.perf.MeterFrameCounter
import audio.tembra.ui.session.InputTelemetry
import audio.tembra.ui.session.MixTelemetry
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.TimelineCallbacks

/**
 * The mixer filling a window of its own (feature 012, spec FR-021a; contracts/mixer-ui.md): the strip row and the pinned
 * master in the full form (the dial drops below 480 dp of window height, the knob below 292, as in the sheet), over the
 * same telemetry read once per frame at the root (research R6) and its own focus-restoring root. `paneTitle` "Mixer",
 * tag `mixer.window`. No selection: the timeline's selection stays in its window.
 */
@Composable
internal fun MixerWindowContent(
    state: MixerWindowUiState,
    callbacks: TimelineCallbacks,
    telemetry: MixTelemetry,
    inputTelemetry: InputTelemetry,
    modifier: Modifier = Modifier,
) {
    val daw = TembraTheme.daw
    val spacing = TembraTheme.spacing
    val feed = rememberMeterFeed(active = state.open, strips = state.strips, inputTelemetry = inputTelemetry)
    val focusMemory = remember { FocusMemory() }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(daw.surfaceLevel2)
            .focusRestoringRoot(focusMemory)
            .testTag(MixerTestTags.WINDOW)
            .semantics { paneTitle = MixerCopy.mixer }
            // One frame of the window: the one telemetry read, then every strip's draw (contracts/perf-instrumentation.md).
            .drawWithContent {
                PerfTrace.section(PerfSection.TIMELINE_MIXER_FRAME) {
                    val frameNanos = feed.tick.longValue
                    telemetry.read(frameNanos, feed.levels)
                    MeterFrameCounter.shared.frame(frameNanos)
                    drawContent()
                }
            },
    ) {
        val form = StripForm.of(MixerHeight.Full, maxHeight.value)
        Row(modifier = Modifier.fillMaxSize().padding(vertical = spacing.sm)) {
            MixerStripRow(strips = state.strips, form = form, selectedIndex = null, callbacks = callbacks, feed = feed, modifier = Modifier.weight(1f), canReorder = state.canReorder)
            MasterStrip(master = state.master, callbacks = callbacks, feed = feed, modifier = Modifier.padding(end = spacing.pageMargin))
        }
    }
}
