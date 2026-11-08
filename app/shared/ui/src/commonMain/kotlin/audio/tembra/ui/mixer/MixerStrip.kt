package audio.tembra.ui.mixer

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import audio.tembra.engine.CaptureSnapshot
import audio.tembra.engine.perf.PerfSection
import audio.tembra.ui.designsystem.Fader
import audio.tembra.ui.designsystem.FaderScale
import audio.tembra.ui.designsystem.LevelMeter
import audio.tembra.ui.designsystem.MeterReading
import audio.tembra.ui.designsystem.PanKnob
import audio.tembra.ui.designsystem.VuDial
import audio.tembra.ui.designsystem.onSecondaryClick
import audio.tembra.ui.designsystem.pointerMenuAnchor
import audio.tembra.ui.designsystem.pointerMenuItem
import audio.tembra.ui.designsystem.rememberPointerMenuAnchor
import audio.tembra.engine.perf.PerfTrace
import audio.tembra.ui.perf.traceFirstDraw
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.TimelineCallbacks
import audio.tembra.ui.timeline.TimelineTestTags
import audio.tembra.ui.timeline.components.MuteSoloButton
import audio.tembra.ui.timeline.components.TrackMenu

/**
 * One track's strip (feature 012, spec FR-002, FR-007, FR-012 to FR-014; research R2; contracts/mixer-ui.md
 * "MixerStrip"): top to bottom the number, the VU dial in the full form, the pan knob in both full forms, Solo and Mute side by side,
 * the fader with the two-bar level meter beside it, and the name, over the track's tint band at the base. The meter
 * reads the strip's post-fader output from the sheet's [feed], or the microphone while the track is armed
 * ([MeterSource.Input]); its clip square clears the matching latch. The first strip's meter is the `timeline.mixerOpen`
 * section's end. One focus group, so Tab walks the knob (where shown), Solo, Mute, then the fader. The number and the
 * name select the track.
 */
@Composable
internal fun MixerStrip(
    strip: StripUi,
    form: StripForm,
    selected: Boolean,
    callbacks: TimelineCallbacks,
    feed: MeterFeed,
    modifier: Modifier = Modifier,
    /** The track menu's availability (feature 011): the ends of the row and whether a take runs. */
    isFirst: Boolean = false,
    isLast: Boolean = false,
    canReorder: Boolean = true,
) {
    val daw = TembraTheme.daw
    val colors = TembraTheme.colors
    val spacing = TembraTheme.spacing
    val density = LocalDensity.current
    val tint = daw.trackTints[strip.colorIndex % daw.trackTints.size]
    val band = if (strip.audible) tint else tint.copy(alpha = DIMMED_ALPHA)
    val index = strip.index
    val text = if (strip.audible) colors.onSurface else colors.onSurfaceVariant
    val onSelect = { callbacks.onStripSelect(strip.id) }
    val menuAnchor = rememberPointerMenuAnchor()
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
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
    Box(
        modifier = modifier
            // 72 dp at every font size: the labels grow in height and the name ellipsises (spec FR-025, FR-026).
            .width(MixerStripDefaults.Width)
            .fillMaxHeight()
            .testTag(MixerTestTags.strip(index))
            .focusGroup()
            // The catalogue's Mixer scope on any focused control of the strip (spec FR-028): M and O toggle this track,
            // the arrows, Page and Home reach its fader from Solo, Mute or the name; the root's handler never sees them.
            .mixerKeyHandling(remember(strip, callbacks) { StripKeyTargets(strip, callbacks) })
            // A secondary click anywhere on the strip opens the track menu at the pointer (feature 011, spec FR-012).
            .pointerMenuItem(menuAnchor)
            .onSecondaryClick(strip.id) { position ->
                PerfTrace.section(PerfSection.TIMELINE_CONTEXT_MENU) {
                    menuOffset = menuAnchor.offsetFor(position, density)
                    menuOpen = true
                }
            }
            .drawBehind {
                val height = MixerStripDefaults.BandHeight.toPx()
                drawRect(band, topLeft = Offset(0f, size.height - height), size = Size(size.width, height))
            }
            .padding(vertical = spacing.xs),
    ) {
        // The menu's anchor takes no room: a point at the strip's top-left corner the pointer offset is measured from.
        TrackMenu(
            expanded = menuOpen,
            isFirst = isFirst,
            isLast = isLast,
            onDismiss = { menuOpen = false },
            onSettings = { callbacks.onTrackSettings(strip.id) },
            onAddAudio = { callbacks.onAddAudio(strip.id) },
            onMoveUp = { callbacks.onMoveTrack(strip.id, -1) },
            onMoveDown = { callbacks.onMoveTrack(strip.id, 1) },
            onDelete = { callbacks.onDeleteTrack(strip.id) },
            modifier = Modifier.align(Alignment.TopStart),
            testTag = TimelineTestTags.TRACK_MENU,
            canReorder = canReorder,
            offset = menuOffset,
        ) {
            Box(Modifier.pointerMenuAnchor(menuAnchor))
        }
        StripBody(
            strip = strip,
            form = form,
            selected = selected,
            callbacks = callbacks,
            feed = feed,
            reading = reading,
            onResetClip = onResetClip,
            onSelect = onSelect,
            text = text,
        )
    }
}

/** The strip's controls top to bottom (research R2): number, dial and knob in the full forms, Solo and Mute, the fader with its meter, the name. */
@Composable
private fun StripBody(
    strip: StripUi,
    form: StripForm,
    selected: Boolean,
    callbacks: TimelineCallbacks,
    feed: MeterFeed,
    reading: MeterReading,
    onResetClip: () -> Unit,
    onSelect: () -> Unit,
    text: Color,
) {
    val colors = TembraTheme.colors
    val spacing = TembraTheme.spacing
    val index = strip.index
    Column(
        modifier = Modifier.fillMaxSize(),
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
        if (form.showsPan) {
            PanKnob(
                pan = strip.pan,
                onPan = { callbacks.onPan(strip.id, it) },
                onSettled = callbacks.onPanSettled,
                description = MixerCopy.panDescription(strip.name),
                testTag = MixerTestTags.pan(index),
            )
        }
        // The design's verdicts (contracts/mixer-ui.md): Solo lit in lavender, Mute dim; the same state as the header's S and M (FR-010).
        Row(horizontalArrangement = Arrangement.Center) {
            MuteSoloButton(
                letter = SOLO_LETTER,
                description = MixerCopy.solo,
                checked = strip.soloed,
                tint = colors.primary,
                onCheckedChange = { callbacks.onToggleSolo(strip.id) },
                size = MixerStripDefaults.ToggleSize,
                testTag = MixerTestTags.solo(index),
            )
            MuteSoloButton(
                letter = MUTE_LETTER,
                description = MixerCopy.mute,
                checked = strip.muted,
                tint = colors.primary.copy(alpha = MUTE_LIT_ALPHA),
                onCheckedChange = { callbacks.onToggleMute(strip.id) },
                size = MixerStripDefaults.ToggleSize,
                testTag = MixerTestTags.mute(index),
                checkedContent = colors.onSurface,
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

/** The number or the name: a row of at least 20 dp (taller at a larger font) that selects the track; the selected strip's rows read as selected. */
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
            .heightIn(min = MixerStripDefaults.LabelHeight)
            .testTag(testTag)
            // A tap or a screen reader selects the track; the Tab order is the strip's controls alone (contracts/mixer-ui.md "Keyboard").
            .focusProperties { canFocus = false }
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

/** A muted strip's M: the lavender at a third, the "dim" of the design table. */
private const val MUTE_LIT_ALPHA = 0.35f
