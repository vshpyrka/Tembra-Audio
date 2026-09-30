package audio.tembra.ui.metronome.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import audio.tembra.core.metronome.BuiltInClickSound
import audio.tembra.core.metronome.ClickSound
import audio.tembra.ui.designsystem.drawFocusRing
import audio.tembra.ui.metronome.CustomSoundState
import audio.tembra.ui.metronome.MetronomeCopy
import audio.tembra.ui.theme.TembraIcon
import audio.tembra.ui.theme.TembraIcons
import audio.tembra.ui.theme.TembraShapes
import audio.tembra.ui.theme.TembraTheme

/**
 * The click sound picker (spec FR-021, FR-022; contracts/design-system-additions.md): the five built-in sounds in the
 * spec's order, then Custom. [ClickSoundPickerLayout.Dropdown] is a pill naming the current sound (tagged [testTag],
 * announced "Click sound, Classic Wood") that opens a 160 dp menu on `surfaceContainerHighest` with a check on the
 * current entry; [ClickSoundPickerLayout.Tiles] is the two-column grid of [ClickSoundTile]s. Entries are tagged
 * `<testTag>.<id>` (`metronome.sound.808-rim`, `metronome.sound.custom`).
 *
 * The current entry selects nothing again. While nothing is imported the Custom entry reads "Custom" with a "+" and
 * calls [onImportCustom]; an imported one is selected through [onSelect]. [onReplaceCustom] serves the filled Custom
 * entry's Replace action (User Story 5).
 */
@Composable
internal fun ClickSoundPicker(
    sound: ClickSound,
    customSound: CustomSoundState,
    layout: ClickSoundPickerLayout,
    onSelect: (ClickSound) -> Unit,
    onImportCustom: () -> Unit,
    onReplaceCustom: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "metronome.sound",
) {
    val customName = when (customSound) {
        is CustomSoundState.Available -> customSound.displayName
        is CustomSoundState.Unavailable -> customSound.displayName
        CustomSoundState.None, CustomSoundState.Importing -> null
    }
    val customSelected = sound is ClickSound.Custom && customSound is CustomSoundState.Available
    val onCustom: () -> Unit = {
        when (customSound) {
            is CustomSoundState.Available -> if (!customSelected) onSelect(ClickSound.Custom(customSound.displayName))
            CustomSoundState.None, is CustomSoundState.Unavailable -> onImportCustom()
            CustomSoundState.Importing -> Unit
        }
    }
    when (layout) {
        ClickSoundPickerLayout.Dropdown -> SoundDropdown(
            sound = sound,
            customName = customName,
            customSelected = customSelected,
            onSelect = onSelect,
            onCustom = onCustom,
            testTag = testTag,
            modifier = modifier,
        )
        ClickSoundPickerLayout.Tiles -> SoundTiles(
            sound = sound,
            customName = customName,
            customSelected = customSelected,
            onSelect = onSelect,
            onCustom = onCustom,
            testTag = testTag,
            modifier = modifier,
        )
    }
}

/** The pill and its menu: the pill is `surfaceContainerHigh` (hover `surfaceContainerHighest`), fully rounded, drawn inside a 44 dp target. */
@Composable
private fun SoundDropdown(
    sound: ClickSound,
    customName: String?,
    customSelected: Boolean,
    onSelect: (ClickSound) -> Unit,
    onCustom: () -> Unit,
    testTag: String,
    modifier: Modifier,
) {
    val colors = TembraTheme.colors
    val spacing = TembraTheme.spacing
    var open by remember { mutableStateOf(false) }
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    val fill = if (hovered || pressed) colors.surfaceContainerHighest else colors.surfaceContainerHigh
    val ring = spacing.focusRingWidth
    val ringColor = colors.primary
    val name = nameOf(sound)

    Box(modifier = modifier) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides spacing.touchTargetMin) {
            Box(
                modifier = Modifier
                    .testTag(testTag)
                    .clickable(interactionSource = source, indication = null, role = Role.Button) { open = true }
                    .semantics { contentDescription = MetronomeCopy.clickSound(name) }
                    .minimumInteractiveComponentSize(),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = Modifier
                        .drawBehind {
                            val radius = CornerRadius(size.height / 2)
                            drawRoundRect(color = fill, cornerRadius = radius)
                            if (focused) drawFocusRing(ringColor, ring.toPx(), radius)
                        }
                        .padding(horizontal = spacing.md, vertical = MetronomeDefaults.SoundPillVerticalPadding),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(MetronomeDefaults.SoundTileGap), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = TembraIcons.vector(TembraIcon.GraphicEq),
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(MetronomeDefaults.SoundPillIconSize),
                        )
                        Text(text = name, style = TembraTheme.typography.labelMedium, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(
                        imageVector = TembraIcons.vector(TembraIcon.ExpandMore),
                        contentDescription = null,
                        tint = colors.outline,
                        modifier = Modifier.size(MetronomeDefaults.SoundPillChevronSize),
                    )
                }
            }
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.width(spacing.soundMenuWidth),
            shape = RoundedCornerShape(TembraShapes.medium),
            containerColor = colors.surfaceContainerHighest,
        ) {
            for (builtIn in BuiltInClickSound.entries) {
                val entry = ClickSound.BuiltIn(builtIn)
                val selected = sound == entry
                SoundMenuItem(
                    label = builtIn.label,
                    selected = selected,
                    trailing = if (selected) TembraIcon.Check else null,
                    trailingTag = "$testTag.${builtIn.id}.check",
                    testTag = "$testTag.${builtIn.id}",
                    onClick = {
                        open = false
                        if (!selected) onSelect(entry)
                    },
                )
            }
            SoundMenuItem(
                label = customName ?: MetronomeCopy.custom,
                selected = customSelected,
                trailing = when {
                    customSelected -> TembraIcon.Check
                    customName == null -> ClickSoundGlyphs.custom(filled = false)
                    else -> null
                },
                trailingTag = if (customSelected) "$testTag.$CUSTOM_ID.check" else "$testTag.$CUSTOM_ID.add",
                testTag = "$testTag.$CUSTOM_ID",
                contentDescription = MetronomeCopy.customEntry(customName),
                onClick = {
                    open = false
                    onCustom()
                },
            )
        }
    }
}

/** One menu entry: `labelMedium`, `primary` when current and `onSurfaceVariant` otherwise, with an optional 16 dp trailing glyph. */
@Composable
private fun SoundMenuItem(
    label: String,
    selected: Boolean,
    trailing: TembraIcon?,
    trailingTag: String,
    testTag: String,
    onClick: () -> Unit,
    contentDescription: String? = null,
) {
    val colors = TembraTheme.colors
    val tint = if (selected) colors.primary else colors.onSurfaceVariant
    DropdownMenuItem(
        text = { Text(text = label, style = TembraTheme.typography.labelMedium, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        onClick = onClick,
        modifier = Modifier
            .testTag(testTag)
            .semantics {
                this.selected = selected
                if (contentDescription != null) this.contentDescription = contentDescription
            },
        trailingIcon = trailing?.let { icon ->
            {
                Icon(
                    imageVector = TembraIcons.vector(icon),
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(MetronomeDefaults.SoundGlyphSize).testTag(trailingTag),
                )
            }
        },
        contentPadding = PaddingValues(horizontal = TembraTheme.spacing.md),
    )
}

/** The expanded layout's grid: two columns of 44 dp tiles, 6 dp apart, in the spec's order with Custom last. */
@Composable
private fun SoundTiles(
    sound: ClickSound,
    customName: String?,
    customSelected: Boolean,
    onSelect: (ClickSound) -> Unit,
    onCustom: () -> Unit,
    testTag: String,
    modifier: Modifier,
) {
    val count = BuiltInClickSound.entries.size + 1
    Column(modifier = modifier.testTag(testTag), verticalArrangement = Arrangement.spacedBy(MetronomeDefaults.SoundTileGap)) {
        for (row in 0 until (count + 1) / 2) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MetronomeDefaults.SoundTileGap)) {
                for (column in 0 until 2) {
                    val index = row * 2 + column
                    val cell = Modifier.weight(1f)
                    val builtIn = BuiltInClickSound.entries.getOrNull(index)
                    when {
                        builtIn != null -> {
                            val entry = ClickSound.BuiltIn(builtIn)
                            val selected = sound == entry
                            ClickSoundTile(
                                label = builtIn.label,
                                icon = ClickSoundGlyphs.of(builtIn),
                                selected = selected,
                                onClick = { if (!selected) onSelect(entry) },
                                modifier = cell,
                                testTag = "$testTag.${builtIn.id}",
                            )
                        }
                        index == count - 1 -> ClickSoundTile(
                            label = customName ?: MetronomeCopy.custom,
                            icon = ClickSoundGlyphs.custom(filled = customName != null),
                            selected = customSelected,
                            onClick = onCustom,
                            modifier = cell.semantics { contentDescription = MetronomeCopy.customEntry(customName) },
                            dashed = customName == null,
                            testTag = "$testTag.$CUSTOM_ID",
                        )
                        else -> Spacer(cell)
                    }
                }
            }
        }
    }
}

private fun nameOf(sound: ClickSound): String = when (sound) {
    is ClickSound.BuiltIn -> sound.sound.label
    is ClickSound.Custom -> sound.displayName
}

private const val CUSTOM_ID = "custom"
