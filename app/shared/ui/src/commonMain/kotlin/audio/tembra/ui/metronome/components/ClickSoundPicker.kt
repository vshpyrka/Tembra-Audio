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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberTooltipState
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
import androidx.compose.ui.semantics.clearAndSetSemantics
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
 * The current entry selects nothing again. The Custom entry follows [customSound] (spec FR-022):
 * - [CustomSoundState.None]: "Custom" with a "+" (the dashed tile), calling [onImportCustom];
 * - [CustomSoundState.Available]: the file's name, selected through [onSelect], with a Replace icon button (tagged
 *   `<testTag>.replace`, announced and tooltipped "Replace custom sound") calling [onReplaceCustom];
 * - [CustomSoundState.Unavailable]: the name greyed (`onSurfaceVariant`) with a "+", calling [onImportCustom];
 * - [CustomSoundState.Importing]: disabled while a file is picked and prepared.
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
    val onReplace = if (customSound is CustomSoundState.Available) onReplaceCustom else null
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
            customSound = customSound,
            customName = customName,
            customSelected = customSelected,
            onSelect = onSelect,
            onCustom = onCustom,
            onReplace = onReplace,
            testTag = testTag,
            modifier = modifier,
        )
        ClickSoundPickerLayout.Tiles -> SoundTiles(
            sound = sound,
            customSound = customSound,
            customName = customName,
            customSelected = customSelected,
            onSelect = onSelect,
            onCustom = onCustom,
            onReplace = onReplace,
            testTag = testTag,
            modifier = modifier,
        )
    }
}

/** The pill and its menu: the pill is `surfaceContainerHigh` (hover `surfaceContainerHighest`), fully rounded, drawn inside a 44 dp target. */
@Composable
private fun SoundDropdown(
    sound: ClickSound,
    customSound: CustomSoundState,
    customName: String?,
    customSelected: Boolean,
    onSelect: (ClickSound) -> Unit,
    onCustom: () -> Unit,
    onReplace: (() -> Unit)?,
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
                        // The pill's description already names the sound: the drawn name stays out of the merged node.
                        Text(
                            text = name,
                            style = TembraTheme.typography.labelMedium,
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("$testTag.label").clearAndSetSemantics {},
                        )
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
                trailing = when (customSound) {
                    is CustomSoundState.Available -> if (customSelected) TembraIcon.Check else null
                    CustomSoundState.None, is CustomSoundState.Unavailable -> ClickSoundGlyphs.custom(filled = false)
                    CustomSoundState.Importing -> null
                },
                trailingTag = if (customSelected) "$testTag.$CUSTOM_ID.check" else "$testTag.$CUSTOM_ID.add",
                testTag = "$testTag.$CUSTOM_ID",
                contentDescription = MetronomeCopy.customEntry(customName),
                enabled = customSound != CustomSoundState.Importing,
                action = onReplace?.let { replace ->
                    {
                        ReplaceCustomButton(
                            onReplace = {
                                open = false
                                replace()
                            },
                            testTag = "$testTag.$REPLACE_ID",
                        )
                    }
                },
                onClick = {
                    open = false
                    onCustom()
                },
            )
        }
    }
}

/**
 * One menu entry: `labelMedium`, `primary` when current and `onSurfaceVariant` otherwise (at the disabled alpha when not
 * [enabled]), with an optional 16 dp trailing glyph and an [action] after it (the Custom entry's Replace button).
 */
@Composable
private fun SoundMenuItem(
    label: String,
    selected: Boolean,
    trailing: TembraIcon?,
    trailingTag: String,
    testTag: String,
    onClick: () -> Unit,
    contentDescription: String? = null,
    enabled: Boolean = true,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = TembraTheme.colors
    val tint = when {
        !enabled -> colors.onSurface.copy(alpha = MetronomeDefaults.DISABLED_ALPHA)
        selected -> colors.primary
        else -> colors.onSurfaceVariant
    }
    DropdownMenuItem(
        text = { Text(text = label, style = TembraTheme.typography.labelMedium, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        onClick = onClick,
        modifier = Modifier
            .testTag(testTag)
            .semantics {
                this.selected = selected
                if (contentDescription != null) this.contentDescription = contentDescription
            },
        trailingIcon = if (trailing == null && action == null) {
            null
        } else {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (trailing != null) {
                        Icon(
                            imageVector = TembraIcons.vector(trailing),
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(MetronomeDefaults.SoundGlyphSize).testTag(trailingTag),
                        )
                    }
                    if (action != null) action()
                }
            }
        },
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = TembraTheme.spacing.md),
    )
}

/** The expanded layout's grid: two columns of 44 dp tiles, 6 dp apart, in the spec's order with Custom last. */
@Composable
private fun SoundTiles(
    sound: ClickSound,
    customSound: CustomSoundState,
    customName: String?,
    customSelected: Boolean,
    onSelect: (ClickSound) -> Unit,
    onCustom: () -> Unit,
    onReplace: (() -> Unit)?,
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
                            icon = ClickSoundGlyphs.custom(filled = customSound is CustomSoundState.Available),
                            selected = customSelected,
                            onClick = onCustom,
                            modifier = cell.semantics { contentDescription = MetronomeCopy.customEntry(customName) },
                            dashed = customName == null,
                            enabled = customSound != CustomSoundState.Importing,
                            muted = customSound is CustomSoundState.Unavailable,
                            trailing = onReplace?.let { replace ->
                                { ReplaceCustomButton(onReplace = replace, testTag = "$testTag.$REPLACE_ID") }
                            },
                            testTag = "$testTag.$CUSTOM_ID",
                        )
                        else -> Spacer(cell)
                    }
                }
            }
        }
    }
}

/**
 * The filled Custom entry's Replace action (spec FR-022, FR-029): an icon-only `swap_horiz` button in a 44 dp target,
 * `onSurfaceVariant` (hover `primary`), announced and tooltipped "Replace custom sound" (hover or long press). Keyboard
 * focus draws a `primary` ring.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReplaceCustomButton(onReplace: () -> Unit, testTag: String, modifier: Modifier = Modifier) {
    val colors = TembraTheme.colors
    val spacing = TembraTheme.spacing
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    val ring = spacing.focusRingWidth
    val ringColor = colors.primary
    Box(modifier = modifier) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            tooltip = { PlainTooltip { Text(MetronomeCopy.replaceCustomSound) } },
            state = rememberTooltipState(),
        ) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides spacing.touchTargetMin) {
                Box(
                    modifier = Modifier
                        .testTag(testTag)
                        .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onReplace)
                        .semantics { contentDescription = MetronomeCopy.replaceCustomSound }
                        .minimumInteractiveComponentSize()
                        .drawBehind { if (focused) drawFocusRing(ringColor, ring.toPx(), CornerRadius(size.minDimension / 2)) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = TembraIcons.vector(TembraIcon.SwapHoriz),
                        contentDescription = null,
                        tint = if (hovered) colors.primary else colors.onSurfaceVariant,
                        modifier = Modifier.size(MetronomeDefaults.SoundGlyphSize),
                    )
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
private const val REPLACE_ID = "replace"
