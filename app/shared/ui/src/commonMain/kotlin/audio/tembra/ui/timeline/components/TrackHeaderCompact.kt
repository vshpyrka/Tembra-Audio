package audio.tembra.ui.timeline.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import audio.tembra.ui.session.InputTelemetry
import audio.tembra.engine.perf.PerfSection
import audio.tembra.engine.perf.PerfTrace
import audio.tembra.ui.designsystem.focusIdentity
import audio.tembra.ui.designsystem.onSecondaryClick
import audio.tembra.ui.designsystem.pointerMenuAnchor
import audio.tembra.ui.designsystem.pointerMenuItem
import audio.tembra.ui.designsystem.rememberPointerMenuAnchor
import audio.tembra.ui.theme.TembraIcon
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.TimelineTestTags
import audio.tembra.ui.timeline.TrackUi
import audio.tembra.ui.transport.RecordButton
import audio.tembra.ui.transport.RecordButtonState
import audio.tembra.ui.transport.TransportButton

/**
 * The compact and medium track header (contracts/timeline-screen.md; the pulled phone screen): a 56 dp row above the
 * lane with the track's colour dot, name, instrument chip, the square M and S toggles tinted by the track and the
 * menu. Tapping the row selects the track; a selected row sits on `surface_level2`. Described as
 * "Track 2, Guitar, −2.4dB, muted" (FR-058).
 */
@Composable
internal fun TrackHeaderCompact(
    track: TrackUi,
    index: Int,
    selected: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onSelect: () -> Unit,
    onMute: (Boolean) -> Unit,
    onSolo: (Boolean) -> Unit,
    onSettings: () -> Unit,
    onAddAudio: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    onArm: (Boolean) -> Unit = {},
    onRecord: () -> Unit = {},
    recordState: RecordButtonState = RecordButtonState.Idle,
    /** False while a take runs: Move up, Move down and Delete track are unavailable (feature 010, spec FR-018). */
    canReorder: Boolean = true,
    /** The armed track's input, read per frame by the meter (feature 010, spec FR-005); null leaves the meter out. */
    inputTelemetry: InputTelemetry? = null,
    onResetClip: () -> Unit = {},
    menuOpen: Boolean,
    onMenuOpen: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TembraTheme.colors
    val daw = TembraTheme.daw
    val spacing = TembraTheme.spacing
    val tint = daw.trackTints[track.colorIndex % daw.trackTints.size]
    // A secondary click anywhere on the header opens the track menu at the pointer (feature 011, spec FR-012).
    val density = LocalDensity.current
    val menuAnchor = rememberPointerMenuAnchor()
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(HeaderHeight)
            .background(if (selected) daw.surfaceLevel2 else daw.surfaceLevel1)
            .testTag(TimelineTestTags.header(index))
            // Keyboard focus follows the track, not the row index or the form (feature 011, spec FR-007).
            .focusIdentity("timeline.track.${track.id.value}")
            .pointerMenuItem(menuAnchor)
            .onSecondaryClick(track.id) { position ->
                PerfTrace.section(PerfSection.TIMELINE_CONTEXT_MENU) {
                    menuOffset = menuAnchor.offsetFor(position, density)
                    onMenuOpen(true)
                }
            }
            .pointerHoverIcon(PointerIcon.Hand).selectable(selected = selected, role = Role.Button, onClick = onSelect)
            .semantics { contentDescription = track.description },
    ) {
    // The input meter lies along the bottom edge, over the row, so the header keeps its 56 dp (spec FR-005).
    if (track.armed && inputTelemetry != null) {
        InputMeter(
            telemetry = inputTelemetry,
            armed = true,
            onResetClip = onResetClip,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            testTag = TimelineTestTags.meter(index),
            clipTestTag = TimelineTestTags.clipIndicator(index),
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(HeaderHeight)
            .padding(start = spacing.md, end = spacing.xs),
        // xs gaps: with Record shown the 44 dp controls (Record, M, S, arm, menu) plus a 72 dp name must fit 360 dp.
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(DotSize).background(if (track.audible) tint else tint.copy(alpha = DimmedAlpha), CircleShape))
        Text(
            text = track.name,
            style = TembraTheme.typography.labelLarge,
            color = if (track.audible) colors.onSurface else colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).widthIn(min = NameMinWidth).padding(start = spacing.xs),
        )
        // While armed with Record available here the chip makes room for the Record control (contract, "Layout rules").
        if (track.armed && track.recordHere) {
            RecordButton(state = recordState, onRecord = onRecord, testTag = TimelineTestTags.record(index))
        } else {
            track.label?.let { InstrumentChip(it) }
        }
        Box(Modifier.weight(1f))
        MuteSoloButton(letter = "M", description = MUTE_DESCRIPTION, checked = track.muted, tint = tint, onCheckedChange = onMute, testTag = TimelineTestTags.mute(index))
        MuteSoloButton(letter = "S", description = SOLO_DESCRIPTION, checked = track.soloed, tint = tint, onCheckedChange = onSolo, testTag = TimelineTestTags.solo(index))
        ArmButton(armed = track.armed, trackTint = tint, onToggle = onArm, testTag = TimelineTestTags.arm(index))
        TrackMenu(
            expanded = menuOpen,
            isFirst = isFirst,
            isLast = isLast,
            onDismiss = { onMenuOpen(false) },
            onSettings = onSettings,
            onAddAudio = onAddAudio,
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown,
            onDelete = onDelete,
            testTag = TimelineTestTags.TRACK_MENU,
            canReorder = canReorder,
            offset = menuOffset,
        ) {
            TransportButton(
                icon = TembraIcon.MoreVert,
                description = TRACK_MENU_DESCRIPTION,
                onClick = {
                    menuOffset = DpOffset.Zero
                    onMenuOpen(true)
                },
                modifier = Modifier.pointerMenuAnchor(menuAnchor),
                testTag = TimelineTestTags.trackMenu(index),
            )
        }
    }
    }
}

internal const val MUTE_DESCRIPTION = "Mute"
internal const val SOLO_DESCRIPTION = "Solo"
internal const val TRACK_MENU_DESCRIPTION = "Track menu"
internal val HeaderHeight = 56.dp
internal val NameMinWidth = 72.dp
internal val DotSize = 10.dp
internal const val DimmedAlpha = 0.4f
