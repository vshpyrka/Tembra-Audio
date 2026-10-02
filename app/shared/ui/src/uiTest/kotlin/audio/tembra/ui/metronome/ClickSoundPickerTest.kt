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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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
import androidx.compose.ui.test.performMouseInput
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
 * the Tiles grid, and the Custom entry: empty (User Story 4), and filled, unavailable and importing with the Replace
 * action (User Story 5, spec FR-022).
 */
@OptIn(ExperimentalTestApi::class)
class ClickSoundPickerTest {

    private val selected = mutableListOf<ClickSound>()
    private var imports = 0
    private var replaces = 0
    private var secondary = Color.Unspecified
    private var primary = Color.Unspecified
    private var onSurface = Color.Unspecified
    private var onSurfaceVariant = Color.Unspecified
    private var pixelDensity = 1f

    private val ids = listOf("classic-wood", "808-rim", "digital-beep", "cowbell", "mechanical", "custom")

    private fun ComposeUiTest.setPicker(
        layout: ClickSoundPickerLayout,
        sound: ClickSound = ClickSound.BuiltIn(BuiltInClickSound.ClassicWood),
        customSound: CustomSoundState = CustomSoundState.None,
    ) {
        mainClock.autoAdvance = false
        setContent {
            TembraTheme {
                secondary = TembraTheme.colors.secondary
                primary = TembraTheme.colors.primary
                onSurface = TembraTheme.colors.onSurface
                onSurfaceVariant = TembraTheme.colors.onSurfaceVariant
                pixelDensity = LocalDensity.current.density
                // An opaque surface: Android captures a transparent node over the activity's white window.
                Box(Modifier.width(320.dp).background(TembraTheme.colors.surface)) {
                    ClickSoundPicker(
                        sound = sound,
                        customSound = customSound,
                        layout = layout,
                        onSelect = { selected += it },
                        onImportCustom = { imports++ },
                        onReplaceCustom = { replaces++ },
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
        // The drawn name is there, but only the description is read.
        onNodeWithTag("sound.label", useUnmergedTree = true).assertExists()
        onNodeWithTag("sound").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
        onNodeWithTag("sound").assertHeightIsAtLeast(44.dp)
        onNodeWithTag("sound.classic-wood").assertDoesNotExist()
    }

    /** The pill says whether its menu is open, as the signature pill does. */
    @Test
    fun thePillReadsCollapsedOrExpanded() = runComposeUiTest {
        setPicker(ClickSoundPickerLayout.Dropdown)
        onNodeWithTag("sound").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
        onNodeWithTag("sound").performClick()
        settle()
        onNodeWithTag("sound").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
        onNodeWithTag("sound.cowbell").performClick()
        settle()
        assertEquals(listOf<ClickSound>(ClickSound.BuiltIn(BuiltInClickSound.Cowbell)), selected)
        onNodeWithTag("sound").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
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

    // --- User Story 5: the filled, unavailable and importing Custom entry -------------------------------------------------

    private val kick = CustomSoundState.Available("Kick")

    @Test
    fun theFilledCustomEntryNamesItsSoundAndOffersReplaceInTheMenu() = runComposeUiTest {
        setPicker(ClickSoundPickerLayout.Dropdown, customSound = kick)
        onNodeWithTag("sound").performClick()
        settle()
        onNodeWithTag("sound.custom").assertTextEquals("Kick").assertContentDescriptionEquals("Custom, Kick").assertIsEnabled()
        onNodeWithTag("sound.replace").assertContentDescriptionEquals("Replace custom sound").assertHeightIsAtLeast(44.dp)
        onNodeWithTag("sound.replace").performClick()
        settle()
        assertEquals(1, replaces)
        assertEquals(0, imports)
        assertEquals(emptyList(), selected)
    }

    @Test
    fun choosingTheFilledCustomEntrySelectsIt() = runComposeUiTest {
        setPicker(ClickSoundPickerLayout.Dropdown, customSound = kick)
        onNodeWithTag("sound").performClick()
        settle()
        onNodeWithTag("sound.custom").performClick()
        settle()
        assertEquals(listOf<ClickSound>(ClickSound.Custom("Kick")), selected)
        assertEquals(0, imports)
    }

    @Test
    fun theSelectedCustomSoundIsCheckedAndStillReplaceable() = runComposeUiTest {
        setPicker(ClickSoundPickerLayout.Dropdown, sound = ClickSound.Custom("Kick"), customSound = kick)
        onNodeWithTag("sound").assertContentDescriptionEquals("Click sound, Kick")
        onNodeWithTag("sound").performClick()
        settle()
        onNodeWithTag("sound.custom").assertIsSelected()
        onNodeWithTag("sound.custom.check", useUnmergedTree = true).assertExists()
        onNodeWithTag("sound.replace").performClick()
        settle()
        assertEquals(1, replaces)
    }

    @Test
    fun theFilledCustomTileNamesItsSoundWithAReplaceButtonAndTooltip() = runComposeUiTest {
        setPicker(ClickSoundPickerLayout.Tiles, customSound = kick)
        onNodeWithTag("sound.custom").assertTextEquals("Kick").assertContentDescriptionEquals("Custom, Kick")
        onNodeWithTag("sound.replace").assertContentDescriptionEquals("Replace custom sound").assertHeightIsAtLeast(44.dp)
        onNodeWithTag("sound.custom").performClick()
        settle()
        assertEquals(listOf<ClickSound>(ClickSound.Custom("Kick")), selected)
        onNodeWithTag("sound.replace").performClick()
        settle()
        assertEquals(1, replaces)
        assertEquals(0, imports)
        mainClock.autoAdvance = true
        onNodeWithTag("sound.replace").performMouseInput { moveTo(center) }
        waitForIdle()
        onNode(hasText("Replace custom sound")).assertExists()
    }

    /** The label pixel that stands out most from the tile behind it: the text's own colour. */
    private fun ComposeUiTest.labelInk(label: String): Color {
        val image = onNode(hasText(label) and hasAnyAncestor(hasTestTag("sound.custom")), useUnmergedTree = true).captureToImage()
        val pixels = image.toPixelMap()
        val background = pixels[0, 0]
        var ink = background
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            if (pixels[x, y].distanceTo(background) > ink.distanceTo(background)) ink = pixels[x, y]
        }
        return ink
    }

    @Test
    fun anUnavailableCustomTileReadsItsNameGreyedAndImportsAgain() = runComposeUiTest {
        setPicker(ClickSoundPickerLayout.Tiles, customSound = CustomSoundState.Unavailable("Kick"))
        onNodeWithTag("sound.custom").assertTextEquals("Kick").assertContentDescriptionEquals("Custom, Kick").assertIsNotSelected()
        onNodeWithTag("sound.replace").assertDoesNotExist()
        val ink = labelInk("Kick")
        assertTrue(ink.distanceTo(onSurfaceVariant) < ink.distanceTo(onSurface), "the unavailable name is not greyed: $ink")
        assertTrue(ink.distanceTo(onSurfaceVariant) < 0.1f, "the unavailable name is not onSurfaceVariant: $ink")
        onNodeWithTag("sound.custom").performClick()
        settle()
        assertEquals(1, imports)
        assertEquals(emptyList(), selected)
    }

    @Test
    fun anAvailableCustomTileReadsItsNameInOnSurface() = runComposeUiTest {
        setPicker(ClickSoundPickerLayout.Tiles, customSound = kick)
        val ink = labelInk("Kick")
        assertTrue(ink.distanceTo(onSurface) < ink.distanceTo(onSurfaceVariant), "the available name is greyed: $ink")
    }

    @Test
    fun anUnavailableCustomEntryImportsFromTheMenu() = runComposeUiTest {
        setPicker(ClickSoundPickerLayout.Dropdown, customSound = CustomSoundState.Unavailable("Kick"))
        onNodeWithTag("sound").performClick()
        settle()
        onNodeWithTag("sound.custom").assertTextEquals("Kick").assertContentDescriptionEquals("Custom, Kick")
        onNodeWithTag("sound.replace").assertDoesNotExist()
        onNodeWithTag("sound.custom").performClick()
        settle()
        assertEquals(1, imports)
        assertEquals(emptyList(), selected)
    }

    @Test
    fun anImportingCustomEntryIsDisabled() = runComposeUiTest {
        setPicker(ClickSoundPickerLayout.Tiles, customSound = CustomSoundState.Importing)
        onNodeWithTag("sound.custom").assertIsNotEnabled()
        onNodeWithTag("sound.replace").assertDoesNotExist()
        onNodeWithTag("sound.custom").performClick()
        settle()
        assertEquals(0, imports)
        assertEquals(0, replaces)
    }

    @Test
    fun anImportingCustomMenuEntryIsDisabled() = runComposeUiTest {
        setPicker(ClickSoundPickerLayout.Dropdown, customSound = CustomSoundState.Importing)
        onNodeWithTag("sound").performClick()
        settle()
        onNodeWithTag("sound.custom").assertIsNotEnabled()
        onNodeWithTag("sound.replace").assertDoesNotExist()
    }

    @Test
    fun theEmptyCustomTileHasADashedBorder() = runComposeUiTest {
        setPicker(ClickSoundPickerLayout.Tiles)
        // Border runs along the top edge, between the rounded corners: dashes alternate with the tile's fill.
        fun edgeRuns(id: String): Int {
            val image = onNodeWithTag("sound.$id").captureToImage()
            val pixels = image.toPixelMap()
            val y = (pixelDensity / 2).toInt()
            val inside = pixels[image.width / 2, (pixelDensity * 3).toInt()]
            val from = (pixelDensity * 12).toInt()
            val to = image.width - from
            val distances = (from until to).map { pixels[it, y].distanceTo(inside) }
            val threshold = distances.max() / 2
            val marks = distances.map { it > threshold }
            return 1 + marks.zipWithNext().count { (a, b) -> a != b }
        }
        val dashed = edgeRuns("custom")
        assertTrue(dashed >= 6, "the empty Custom tile's border is not dashed: $dashed runs")
        val solid = edgeRuns("808-rim")
        assertTrue(solid <= 2, "a built-in tile's border is dashed: $solid runs")
    }
}
