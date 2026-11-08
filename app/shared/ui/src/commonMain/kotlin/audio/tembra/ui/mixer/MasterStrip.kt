package audio.tembra.ui.mixer

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import audio.tembra.engine.MixSnapshot
import audio.tembra.engine.perf.PerfSection
import audio.tembra.ui.designsystem.Fader
import audio.tembra.ui.designsystem.FaderScale
import audio.tembra.ui.designsystem.LevelMeter
import audio.tembra.ui.designsystem.MeterReading
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.TimelineCallbacks
import audio.tembra.ui.transport.TransportCopy
import kotlin.math.roundToInt

/**
 * The master strip (feature 012, spec FR-001, FR-011, FR-012; contracts/mixer-ui.md "MasterStrip"): the strip's frame
 * with the "Master" label, the master volume as a percent fader (feature 009's 0 … 100 % in whole steps, its default
 * 80 % as the fader's home), the summed output's level meter beside it with its own clip latch (reset through
 * [TimelineCallbacks.onResetMasterClip]) and the percentage under them. No knob, no toggles. Pinned at the right of the
 * strip row by the sheet, on the raised surface.
 */
@Composable
internal fun MasterStrip(
    master: MasterUi,
    callbacks: TimelineCallbacks,
    feed: MeterFeed,
    modifier: Modifier = Modifier,
) {
    val colors = TembraTheme.colors
    val daw = TembraTheme.daw
    val spacing = TembraTheme.spacing
    val reading = remember(feed) { MeterReading.playback(feed.levels, MixSnapshot.MASTER_INDEX) }
    Column(
        modifier = modifier
            .width(MixerStripDefaults.Width)
            .fillMaxHeight()
            .testTag(MixerTestTags.MASTER)
            .focusGroup()
            // The catalogue's Mixer scope for the master: its fader's steps, pages and home (spec FR-028).
            .mixerKeyHandling(remember(master, callbacks) { MasterKeyTargets(master, callbacks) })
            .background(daw.surfaceLevel3)
            .padding(vertical = spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Box(Modifier.fillMaxWidth().heightIn(min = MixerStripDefaults.LabelHeight), contentAlignment = Alignment.Center) {
            Text(MixerCopy.master, style = TembraTheme.typography.labelMedium, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
        // The toggles' row of the track strips, kept empty so the master's fader lines up with theirs.
        Spacer(Modifier.height(MixerStripDefaults.ToggleSize))
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(spacing.xs, Alignment.CenterHorizontally)) {
            Fader(
                value = master.percent.toFloat(),
                onValue = { callbacks.onMasterVolume(it.roundToInt()) },
                onSettled = callbacks.onMasterSettled,
                description = MixerCopy.masterVolume,
                readout = TransportCopy.masterVolumeState(master.percent),
                modifier = Modifier.fillMaxHeight(),
                scale = FaderScale.Percent,
                testTag = MixerTestTags.MASTER_FADER,
                section = PerfSection.TRANSPORT_MASTER_DRAG,
            )
            LevelMeter(
                reading = reading,
                tick = feed.tick,
                level = feed.mirror.master,
                onResetClip = callbacks.onResetMasterClip,
                modifier = Modifier.fillMaxHeight(),
                testTag = MixerTestTags.MASTER_METER,
                clipTestTag = MixerTestTags.MASTER_CLIP,
            )
        }
        Box(Modifier.fillMaxWidth().heightIn(min = MixerStripDefaults.LabelHeight), contentAlignment = Alignment.Center) {
            Text(TransportCopy.masterVolumePercent(master.percent), style = TembraTheme.typographyExtras.tabularParam, color = colors.onSurfaceVariant, maxLines = 1)
        }
    }
}
