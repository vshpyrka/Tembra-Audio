package audio.tembra.ui.timeline.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import audio.tembra.core.project.GainDb
import audio.tembra.ui.session.InputTelemetry
import audio.tembra.ui.theme.TembraIcon
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.TimelineCopy
import audio.tembra.ui.timeline.TimelineTestTags
import audio.tembra.ui.timeline.TrackUi
import audio.tembra.ui.transport.TransportButton

/**
 * The expanded track header (contracts/timeline-screen.md; the arranger tablet screen): the pinned 200 dp column
 * beside the lane with the colour dot, name, instrument chip and menu on the first row, the gain readout, M, S and the
 * chevron that opens the inline settings on the second (spec FR-041). Tapping the header selects the track.
 */
@Composable
internal fun TrackHeader(
    track: TrackUi,
    index: Int,
    selected: Boolean,
    expanded: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onSelect: () -> Unit,
    onMute: (Boolean) -> Unit,
    onSolo: (Boolean) -> Unit,
    onGain: (GainDb) -> Unit,
    onExpand: (Boolean) -> Unit,
    onSettings: () -> Unit,
    onAddAudio: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    onArm: (Boolean) -> Unit = {},
    /** False while a take runs: Move up, Move down and Delete track are unavailable (feature 010, spec FR-018). */
    canReorder: Boolean = true,
    /** The armed track's input, read per frame by the meter beneath row 2 (feature 010, spec FR-005); null leaves it out. */
    inputTelemetry: InputTelemetry? = null,
    onResetClip: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = TembraTheme.colors
    val daw = TembraTheme.daw
    val spacing = TembraTheme.spacing
    val tint = daw.trackTints[track.colorIndex % daw.trackTints.size]
    var menuOpen by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .width(spacing.headerColumnWidth)
            .fillMaxHeight()
            .background(if (selected) daw.surfaceLevel2 else daw.surfaceLevel1)
            .testTag(TimelineTestTags.header(index))
            .selectable(selected = selected, role = Role.Button, onClick = onSelect)
            .semantics { contentDescription = track.description },
    ) {
    // The input meter lies along the bottom edge, in the column's bottom padding, so the header keeps its height (spec
    // FR-005; T099: the 96 dp lane holds the two 44 dp rows and the padding with nothing to spare, so a bar placed in
    // the column's flow beneath the gain row was measured at 0 dp and never drawn).
    if (track.armed && inputTelemetry != null) {
        InputMeter(
            telemetry = inputTelemetry,
            armed = true,
            onResetClip = onResetClip,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = spacing.sm),
            height = spacing.meterHeightExpanded,
            testTag = TimelineTestTags.meter(index),
            clipTestTag = TimelineTestTags.clipIndicator(index),
        )
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = spacing.sm, vertical = spacing.xs),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.size(DotSize).background(if (track.audible) tint else tint.copy(alpha = DimmedAlpha), CircleShape))
            Text(
                text = track.name,
                style = TembraTheme.typography.labelLarge,
                color = if (track.audible) colors.onSurface else colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            track.label?.let { InstrumentChip(it) }
            TrackMenu(
                expanded = menuOpen,
                isFirst = isFirst,
                isLast = isLast,
                onDismiss = { menuOpen = false },
                onSettings = onSettings,
                onAddAudio = onAddAudio,
                onMoveUp = onMoveUp,
                onMoveDown = onMoveDown,
                onDelete = onDelete,
                testTag = TimelineTestTags.TRACK_MENU,
            canReorder = canReorder,
            ) {
                TransportButton(icon = TembraIcon.MoreVert, description = TRACK_MENU_DESCRIPTION, onClick = { menuOpen = true }, testTag = TimelineTestTags.trackMenu(index))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            GainReadout(gain = track.gainDb, onGain = onGain, modifier = Modifier.weight(1f), testTag = TimelineTestTags.gain(index))
            MuteSoloButton(letter = "M", description = MUTE_DESCRIPTION, checked = track.muted, tint = tint, onCheckedChange = onMute, size = SmallToggle, testTag = TimelineTestTags.mute(index))
            MuteSoloButton(letter = "S", description = SOLO_DESCRIPTION, checked = track.soloed, tint = tint, onCheckedChange = onSolo, size = SmallToggle, testTag = TimelineTestTags.solo(index))
            ArmButton(armed = track.armed, trackTint = tint, onToggle = onArm, size = SmallToggle, testTag = TimelineTestTags.arm(index))
            TransportButton(
                icon = if (expanded) TembraIcon.ExpandLess else TembraIcon.ExpandMore,
                description = if (expanded) COLLAPSE_SETTINGS else TimelineCopy.trackSettings,
                onClick = { onExpand(!expanded) },
                testTag = TimelineTestTags.chevron(index),
            )
        }
    }
    }
}

internal const val COLLAPSE_SETTINGS = "Collapse track settings"
private val SmallToggle = androidx.compose.ui.unit.Dp(36f)
