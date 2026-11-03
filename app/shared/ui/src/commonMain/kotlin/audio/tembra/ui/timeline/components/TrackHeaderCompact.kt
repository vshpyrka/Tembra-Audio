package audio.tembra.ui.timeline.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import audio.tembra.ui.designsystem.focusIdentity
import audio.tembra.ui.theme.TembraIcon
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.TimelineTestTags
import audio.tembra.ui.timeline.TrackUi
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
    menuOpen: Boolean,
    onMenuOpen: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TembraTheme.colors
    val daw = TembraTheme.daw
    val spacing = TembraTheme.spacing
    val tint = daw.trackTints[track.colorIndex % daw.trackTints.size]
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(HeaderHeight)
            .background(if (selected) daw.surfaceLevel2 else daw.surfaceLevel1)
            .testTag(TimelineTestTags.header(index))
            // Keyboard focus follows the track, not the row index or the form (feature 011, spec FR-007).
            .focusIdentity("timeline.track.${track.id.value}")
            .selectable(selected = selected, role = Role.Button, onClick = onSelect)
            .semantics { contentDescription = track.description }
            .padding(start = spacing.md, end = spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(DotSize).background(if (track.audible) tint else tint.copy(alpha = DimmedAlpha), CircleShape))
        Text(
            text = track.name,
            style = TembraTheme.typography.labelLarge,
            color = if (track.audible) colors.onSurface else colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        track.label?.let { InstrumentChip(it) }
        Box(Modifier.weight(1f))
        MuteSoloButton(letter = "M", description = MUTE_DESCRIPTION, checked = track.muted, tint = tint, onCheckedChange = onMute, testTag = TimelineTestTags.mute(index))
        MuteSoloButton(letter = "S", description = SOLO_DESCRIPTION, checked = track.soloed, tint = tint, onCheckedChange = onSolo, testTag = TimelineTestTags.solo(index))
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
        ) {
            TransportButton(icon = TembraIcon.MoreVert, description = TRACK_MENU_DESCRIPTION, onClick = { onMenuOpen(true) }, testTag = TimelineTestTags.trackMenu(index))
        }
    }
}

internal const val MUTE_DESCRIPTION = "Mute"
internal const val SOLO_DESCRIPTION = "Solo"
internal const val TRACK_MENU_DESCRIPTION = "Track menu"
internal val HeaderHeight = 56.dp
internal val DotSize = 10.dp
internal const val DimmedAlpha = 0.4f
