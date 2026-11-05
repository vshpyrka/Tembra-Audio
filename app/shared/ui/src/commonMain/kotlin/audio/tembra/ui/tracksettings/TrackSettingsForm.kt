package audio.tembra.ui.tracksettings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import audio.tembra.core.project.ProjectNames
import audio.tembra.ui.designsystem.TembraTextField
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.components.DeleteTrackDialog
import audio.tembra.ui.timeline.components.GainReadout
import audio.tembra.ui.timeline.components.InstrumentLabelMenu
import audio.tembra.ui.timeline.components.MuteSoloButton

/**
 * The fields of one track (spec FR-020, FR-023 to FR-025, FR-041), shared by the nested screen and the inline row:
 * the name (40 characters, committed when the field loses focus), the instrument label menu, the gain slider with
 * its readout, the M/S toggles, the Effects placeholder and "Delete track" behind its confirmation.
 */
@Composable
internal fun TrackSettingsForm(
    state: TrackSettingsUiState,
    callbacks: TrackSettingsCallbacks,
    modifier: Modifier = Modifier,
) {
    val spacing = TembraTheme.spacing
    val colors = TembraTheme.colors
    val tint = TembraTheme.daw.trackTints[state.colorIndex % TembraTheme.daw.trackTints.size]
    var labelMenuOpen by remember { mutableStateOf(false) }
    var nameHadFocus by remember { mutableStateOf(false) }
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = spacing.pageMargin, vertical = spacing.md).testTag(TrackSettingsTestTags.FORM),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        TembraTextField(
            value = state.nameField,
            onValueChange = callbacks.onName,
            label = TrackSettingsCopy.name,
            maxLength = ProjectNames.MAX_TRACK_NAME,
            testTag = TrackSettingsTestTags.NAME,
            modifier = Modifier.fillMaxWidth().onFocusChanged { focus ->
                if (focus.isFocused) nameHadFocus = true else if (nameHadFocus) {
                    nameHadFocus = false
                    callbacks.onNameCommit()
                }
            },
        )
        InstrumentLabelMenu(
            expanded = labelMenuOpen,
            onDismiss = { labelMenuOpen = false },
            onSelect = { label ->
                labelMenuOpen = false
                callbacks.onLabel(label)
            },
            testTag = TrackSettingsTestTags.LABEL_MENU,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = spacing.touchTargetMin)
                    .testTag(TrackSettingsTestTags.LABEL)
                    .semantics { contentDescription = "${TrackSettingsCopy.label}, ${state.labelText}" }
                    .pointerHoverIcon(PointerIcon.Hand).selectable(selected = labelMenuOpen, role = Role.DropdownList, onClick = { labelMenuOpen = true }),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(TrackSettingsCopy.label, style = TembraTheme.typography.bodyLarge, color = colors.onSurface)
                Text(state.labelText, style = TembraTheme.typography.bodyLarge, color = colors.primary)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            Text(TrackSettingsCopy.gain, style = TembraTheme.typography.bodyLarge, color = colors.onSurface)
            GainSlider(gain = state.gainDb, onGain = callbacks.onGain, modifier = Modifier.weight(1f), testTag = TrackSettingsTestTags.GAIN_SLIDER)
            GainReadout(gain = state.gainDb, onGain = callbacks.onGain, testTag = TrackSettingsTestTags.GAIN)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            MuteSoloButton(letter = "M", description = TrackSettingsCopy.mute, checked = state.muted, tint = tint, onCheckedChange = callbacks.onMute, testTag = TrackSettingsTestTags.MUTE)
            MuteSoloButton(letter = "S", description = TrackSettingsCopy.solo, checked = state.soloed, tint = tint, onCheckedChange = callbacks.onSolo, testTag = TrackSettingsTestTags.SOLO)
        }
        EffectsPlaceholder()
        TextButton(onClick = callbacks.onDelete, modifier = Modifier.testTag(TrackSettingsTestTags.DELETE)) {
            Text(TrackSettingsCopy.deleteTrack, color = colors.error)
        }
    }
    if (state.confirmDelete) {
        DeleteTrackDialog(trackName = state.name, onConfirm = callbacks.onConfirmDelete, onCancel = callbacks.onDismissDelete)
    }
}
