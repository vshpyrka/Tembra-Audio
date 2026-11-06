package audio.tembra.ui.mixer

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import audio.tembra.engine.CaptureSnapshot
import audio.tembra.engine.perf.PerfSection
import audio.tembra.ui.designsystem.Fader
import audio.tembra.ui.designsystem.FaderScale
import audio.tembra.ui.designsystem.LevelMeter
import audio.tembra.ui.designsystem.MeterReading
import audio.tembra.ui.designsystem.VuDial
import audio.tembra.ui.perf.traceFirstDraw
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.TimelineCallbacks
import audio.tembra.ui.timeline.components.MuteSoloButton

/**
 * One track's strip (feature 012, spec FR-002, FR-012 to FR-014; research R2; contracts/mixer-ui.md "MixerStrip"): top
 * to bottom the number, the VU dial in the full form (the pan knob joins it in User Story 3), Solo and Mute side by side,
 * the fader with the two-bar level meter beside it, and the name, over the track's tint band at the base. The meter
 * reads the strip's post-fader output from the sheet's [feed], or the microphone while the track is armed
 * ([MeterSource.Input]); its clip square clears the matching latch. The first strip's meter is the `timeline.mixerOpen`
 * section's end. One focus group, so Tab walks Solo, Mute, then the fader. The number and the name select the track.
 */
@Composable
internal fun MixerStrip(
    strip: StripUi,
    form: StripForm,
    selected: Boolean,
    callbacks: TimelineCallbacks,
    feed: MeterFeed,
    modifier: Modifier = Modifier,
) {
    val daw = TembraTheme.daw
    val colors = TembraTheme.colors
    val spacing = TembraTheme.spacing
    val tint = daw.trackTints[strip.colorIndex % daw.trackTints.size]
    val band = if (strip.audible) tint else tint.copy(alpha = DIMMED_ALPHA)
    val index = strip.index
    val text = if (strip.audible) colors.onSurface else colors.onSurfaceVariant
    val onSelect = { callbacks.onStripSelect(strip.id) }
    val reading = remember(index, strip.meterSource, feed) {
        when (strip.meterSource) {
            MeterSource.Playback -> MeterReading.playback(feed.levels, index)
            MeterSource.Input -> MeterReading.input(feed.inputTelemetry, CaptureSnapshot())
        }
    }
    val onResetClip: () -> Unit = when (strip.meterSource) {
        MeterSource.Playback -> ({ callbacks.onResetTrackClip(strip.id) })
        MeterSource.Input -> ({ callbacks.onResetClip(strip.id) })
    }
    Column(
        modifier = modifier
            .width(MixerStripDefaults.Width)
            .fillMaxHeight()
            .testTag(MixerTestTags.strip(index))
            .focusGroup()
            .drawBehind {
                val height = MixerStripDefaults.BandHeight.toPx()
                drawRect(band, topLeft = Offset(0f, size.height - height), size = Size(size.width, height))
            }
            .padding(vertical = spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        StripLabel(
            text = strip.number.toString(),
            style = TembraTheme.typographyExtras.tabularParam,
            color = text,
            selected = selected,
            onSelect = onSelect,
            testTag = MixerTestTags.number(index),
        )
        if (form.showsVu) {
            VuDial(levels = feed.levels, index = index, tick = feed.tick, level = feed.mirror.strip(index), testTag = MixerTestTags.vu(index))
        }
        Row(horizontalArrangement = Arrangement.Center) {
            MuteSoloButton(
                letter = SOLO_LETTER,
                description = MixerCopy.solo,
                checked = strip.soloed,
                tint = tint,
                onCheckedChange = { callbacks.onToggleSolo(strip.id) },
                size = MixerStripDefaults.ToggleSize,
                testTag = MixerTestTags.solo(index),
            )
            MuteSoloButton(
                letter = MUTE_LETTER,
                description = MixerCopy.mute,
                checked = strip.muted,
                tint = tint,
                onCheckedChange = { callbacks.onToggleMute(strip.id) },
                size = MixerStripDefaults.ToggleSize,
                testTag = MixerTestTags.mute(index),
            )
        }
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(spacing.xs, Alignment.CenterHorizontally)) {
            Fader(
                value = FaderScale.Gain.valueOf(strip.gainDb),
                onValue = { callbacks.onGain(strip.id, FaderScale.Gain.gainOf(it)) },
                onSettled = callbacks.onGainSettled,
                description = MixerCopy.gainDescription(strip.name),
                readout = strip.gainDb.readout,
                modifier = Modifier.fillMaxHeight(),
                testTag = MixerTestTags.fader(index),
            )
            LevelMeter(
                reading = reading,
                tick = feed.tick,
                level = feed.mirror.strip(index),
                onResetClip = onResetClip,
                modifier = Modifier.fillMaxHeight().then(if (index == 0) Modifier.traceFirstDraw(PerfSection.TIMELINE_MIXER_OPEN) else Modifier),
                testTag = MixerTestTags.meter(index),
                clipTestTag = MixerTestTags.clip(index),
            )
        }
        StripLabel(
            text = strip.name,
            style = TembraTheme.typography.labelMedium,
            color = text,
            selected = selected,
            onSelect = onSelect,
            testTag = MixerTestTags.name(index),
        )
    }
}

/** The number or the name: a 20 dp row that selects the track; the selected strip's rows read as selected. */
@Composable
private fun StripLabel(
    text: String,
    style: TextStyle,
    color: Color,
    selected: Boolean,
    onSelect: () -> Unit,
    testTag: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(MixerStripDefaults.LabelHeight)
            .testTag(testTag)
            .selectable(selected = selected, role = Role.Button, onClick = onSelect)
            .padding(horizontal = TembraTheme.spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

private const val SOLO_LETTER = "S"
private const val MUTE_LETTER = "M"
private const val DIMMED_ALPHA = 0.4f
