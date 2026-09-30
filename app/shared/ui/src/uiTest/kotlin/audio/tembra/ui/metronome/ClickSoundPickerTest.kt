package audio.tembra.ui.metronome

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import audio.tembra.core.metronome.BuiltInClickSound
import audio.tembra.core.metronome.ClickSound
import audio.tembra.ui.designsystem.averageColorAt
import audio.tembra.ui.designsystem.distanceTo
import audio.tembra.ui.metronome.components.ClickSoundGlyphs
import audio.tembra.ui.metronome.components.ClickSoundPicker
import audio.tembra.ui.metronome.components.ClickSoundPickerLayout
import audio.tembra.ui.metronome.components.ClickSoundTile
import audio.tembra.ui.theme.TembraTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The click sound picker (contracts/design-system-additions.md, spec FR-021, FR-022): the Dropdown pill and its menu,
 * the Tiles grid, and the empty Custom entry. The filled, unavailable and importing Custom states arrive with User
 * Story 5.
 */
@OptIn(ExperimentalTestApi::class)
class ClickSoundPickerTest {

    private val selected = mutableListOf<ClickSound>()
    private var imports = 0
    private var secondary = Color.Unspecified
    private var primary = Color.Unspecified
    private var pixelDensity = 1f

    private val ids = listOf("classic-wood", "808-rim", "digital-beep", "cowbell", "mechanical", "custom")

    private fun ComposeUiTest.setPicker(layout: ClickSoundPickerLayout, sound: ClickSound = ClickSound.BuiltIn(BuiltInClickSound.ClassicWood)) {
        mainClock.autoAdvance = false
        setContent {
            TembraTheme {
                secondary = TembraTheme.colors.secondary
                primary = TembraTheme.colors.primary
                pixelDensity = LocalDensity.current.density
                // An opaque surface: Android captures a transparent node over the activity's white window.
                Box(Modifier.width(320.dp).background(TembraTheme.colors.surface)) {
                    ClickSoundPicker(
                        sound = sound,
                        customSound = CustomSoundState.None,
                        layout = layout,
                        onSelect = { selected += it },
                        onImportCustom = { imports++ },
                        onReplaceCustom = {},
                        testTag = "sound",
                    )
                }
            }
        }
        settle()
    }

    private fun ComposeUiTest.settle() = repeat(3) { mainClock.advanceTimeByFrame() }

    @Test
    fun thePillNamesTheCurrentSound() = runComposeUiTest {
        setPicker(ClickSoundPickerLayout.Dropdown)
        onNodeWithTag("sound").assertContentDescriptionEquals("Click sound, Classic Wood")
        onNode(hasText("Classic Wood") and hasAnyAncestor(hasTestTag("sound")), useUnmergedTree = true).assertExists()
        onNodeWithTag("sound").assertHeightIsAtLeast(44.dp)
        onNodeWithTag("sound.classic-wood").assertDoesNotExist()
    }

    @Test
    fun theMenuListsTheSoundsInOrderAndChecksTheCurrentOne() = runComposeUiTest {
        setPicker(ClickSoundPickerLayout.Dropdown)
        onNodeWithTag("sound").performClick()
        settle()
        val tops = ids.map { onNodeWithTag("sound.$it").getUnclippedBoundsInRoot().top }
        assertEquals(tops.sorted(), tops, "the menu is not in the spec's order")
        assertEquals(ids.size, tops.distinct().size)
        onNodeWithTag("sound.classic-wood").assertIsSelected()
        onNodeWithTag("sound.classic-wood.check", useUnmergedTree = true).assertExists()
        for (id in ids.drop(1)) {
            onNodeWithTag("sound.$id").assertIsNotSelected()
            onNodeWithTag("sound.$id.check", useUnmergedTree = true).assertDoesNotExist()
        }
        listOf("Classic Wood", "808 Rim", "Digital Beep", "Cowbell", "Mechanical", "Custom")
            .zip(ids)
            .forEach { (label, id) -> onNodeWithTag("sound.$id").assertTextEquals(label) }
    }

    @Test
    fun choosingASoundSelectsItAndClosesTheMenu() = runComposeUiTest {
        setPicker(ClickSoundPickerLayout.Dropdown)
        onNodeWithTag("sound").performClick()
        settle()
        onNodeWithTag("sound.cowbell").performClick()
        settle()
        assertEquals(listOf<ClickSound>(ClickSound.BuiltIn(BuiltInClickSound.Cowbell)), selected)
        // The menu's exit animation, then (on Android) the popup window's removal, which is asynchronous.
        mainClock.autoAdvance = true
        waitUntil(timeoutMillis = 2_000) { onAllNodesWithTag("sound.cowbell").fetchSemanticsNodes().isEmpty() }
        onNodeWithTag("sound.cowbell").assertDoesNotExist()
    }

    @Test
    fun choosingTheCurrentSoundSelectsNothing() = runComposeUiTest {
        setPicker(ClickSoundPickerLayout.Dropdown)
        onNodeWithTag("sound").performClick()
        settle()
        onNodeWithTag("sound.classic-wood").performClick()
        settle()
        assertEquals(emptyList(), selected)
    }

    @Test
    fun theEmptyCustomEntryImports() = runComposeUiTest {
        setPicker(ClickSoundPickerLayout.Dropdown)
        onNodeWithTag("sound").performClick()
        settle()
        onNodeWithTag("sound.custom").assertTextEquals("Custom").assertContentDescriptionEquals("Custom, empty")
        onNodeWithTag("sound.custom.add", useUnmergedTree = true).assertExists()
        onNodeWithTag("sound.custom").performClick()
        settle()
        assertEquals(1, imports)
        assertEquals(emptyList(), selected)
    }

    @Test
    fun tilesAreTwoColumnsOfSix() = runComposeUiTest {
        setPicker(ClickSoundPickerLayout.Tiles)
        val bounds = ids.map { onNodeWithTag("sound.$it").getUnclippedBoundsInRoot() }
        assertEquals(2, bounds.map { it.left }.distinct().size, "the tiles are not in two columns")
        assertEquals(3, bounds.map { it.top }.distinct().size, "the tiles are not in three rows")
        for (id in ids) onNodeWithTag("sound.$id").assertHeightIsAtLeast(44.dp)
        onNodeWithTag("sound.classic-wood").assertIsSelected()
        onNodeWithTag("sound.808-rim").assertIsNotSelected()
        onNodeWithTag("sound.custom").assertTextEquals("Custom").assertContentDescriptionEquals("Custom, empty")
        onNodeWithTag("sound.digital-beep").performClick()
        settle()
        onNodeWithTag("sound.custom").performClick()
        settle()
        assertEquals(listOf<ClickSound>(ClickSound.BuiltIn(BuiltInClickSound.DigitalBeep)), selected)
        assertEquals(1, imports)
    }

    @Test
    fun theSelectedTileHasTheSecondaryBorder() = runComposeUiTest {
        setPicker(ClickSoundPickerLayout.Tiles)
        // Mid-height on the left edge, inside the 1 dp border.
        fun edge(id: String): Color {
            val image = onNodeWithTag("sound.$id").captureToImage()
            return image.averageColorAt((pixelDensity / 2).toInt(), image.height / 2, radius = 0)
        }
        val selectedEdge = edge("classic-wood")
        val otherEdge = edge("808-rim")
        assertTrue(selectedEdge.distanceTo(secondary) < 0.1f, "the selected tile's border is not secondary: $selectedEdge")
        assertTrue(otherEdge.distanceTo(secondary) > 0.3f, "an unselected tile has the secondary border: $otherEdge")
    }

    private fun ComposeUiTest.emit(source: MutableInteractionSource, interaction: Interaction) {
        runOnIdle { source.tryEmit(interaction) }
        settle()
    }

    /** Pixels that changed between two captures and moved towards `primary`. */
    private fun changedTowardsPrimary(before: ImageBitmap, after: ImageBitmap): Int {
        val a = before.toPixelMap()
        val b = after.toPixelMap()
        var count = 0
        for (y in 0 until a.height) for (x in 0 until a.width) {
            if (a[x, y].distanceTo(b[x, y]) > 0.05f && b[x, y].distanceTo(primary) < a[x, y].distanceTo(primary)) count++
        }
        return count
    }

    @Test
    fun hoverTurnsAnUnselectedTilePrimary() = runComposeUiTest {
        val source = MutableInteractionSource()
        mainClock.autoAdvance = false
        setContent {
            TembraTheme {
                primary = TembraTheme.colors.primary
                Box(Modifier.width(160.dp).background(TembraTheme.colors.surface)) {
                    ClickSoundTile(
                        label = "808 Rim",
                        icon = ClickSoundGlyphs.of(BuiltInClickSound.Rim808),
                        selected = false,
                        onClick = {},
                        interactionSource = source,
                        testTag = "tile",
                    )
                }
            }
        }
        settle()
        val rest = onNodeWithTag("tile").captureToImage()
        emit(source, HoverInteraction.Enter())
        val hovered = onNodeWithTag("tile").captureToImage()
        assertTrue(changedTowardsPrimary(rest, hovered) > 20, "hover did not turn the tile's text and glyph primary")
    }
}
