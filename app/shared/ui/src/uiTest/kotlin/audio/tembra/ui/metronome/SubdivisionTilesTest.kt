package audio.tembra.ui.metronome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import audio.tembra.core.metronome.BeatUnit
import audio.tembra.core.metronome.Subdivision
import audio.tembra.ui.designsystem.enterKeyboardMode
import audio.tembra.ui.metronome.components.SubdivisionTiles
import audio.tembra.ui.theme.TembraIcon
import audio.tembra.ui.theme.TembraTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The compact layout's subdivision tiles (contracts/design-system-additions.md, design iteration D1): four icon-only
 * 44 dp tiles with the note figure of each subdivision on the beat unit, tabs with `selected`, the value as the
 * description and the hover tooltip.
 */
@OptIn(ExperimentalTestApi::class)
class SubdivisionTilesTest {

    private val chosen = mutableListOf<Subdivision>()
    private var inputModeManager: InputModeManager? = null

    /** The tiles in display order (1, 2, 4, 3 ticks), as their tag ids. */
    private val ids = listOf("1", "2", "4", "3")

    private fun ComposeUiTest.setTiles(selected: Subdivision = Subdivision.One, unit: BeatUnit = BeatUnit.Quarter): (BeatUnit) -> Unit {
        var beatUnit by mutableStateOf(unit)
        setContent {
            inputModeManager = LocalInputModeManager.current
            TembraTheme {
                Box(Modifier.size(320.dp, 200.dp).background(TembraTheme.colors.surface), contentAlignment = Alignment.Center) {
                    SubdivisionTiles(selected = selected, unit = beatUnit, onSelect = { chosen += it })
                }
            }
        }
        waitForIdle()
        return { next -> beatUnit = next; waitForIdle() }
    }

    @Test
    fun fourSquareTilesInOneRowWithTheValueAsTheirName() = runComposeUiTest {
        val setUnit = setTiles()
        val bounds = ids.map { onNodeWithTag("metronome.subdivision.$it").getUnclippedBoundsInRoot() }
        assertEquals(1, bounds.map { it.top }.distinct().size, "the tiles are not one row")
        assertEquals(bounds.sortedBy { it.left }, bounds, "the tiles are not in the order 1, 2, 4, 3")
        for (b in bounds) assertTrue(abs((b.width - b.height).value) < 0.5f, "a tile is ${b.width} × ${b.height}, not square")
        for (id in ids) {
            onNodeWithTag("metronome.subdivision.$id")
                .assertWidthIsAtLeast(44.dp)
                .assertHeightIsAtLeast(44.dp)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
                .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
        }
        ids.zip(listOf("1/4", "1/8", "1/16", "3let")).forEach { (id, label) ->
            onNodeWithTag("metronome.subdivision.$id").assertContentDescriptionEquals(label)
        }
        setUnit(BeatUnit.Eighth)
        ids.zip(listOf("1/8", "1/16", "1/32", "3let")).forEach { (id, label) ->
            onNodeWithTag("metronome.subdivision.$id").assertContentDescriptionEquals(label)
        }
        onNodeWithTag("metronome.subdivision.5").assertDoesNotExist()
    }

    @Test
    fun eachTileShowsTheNoteFigureOfItsBeatUnit() = runComposeUiTest {
        val setUnit = setTiles()
        val quarter = listOf(TembraIcon.NoteQuarter, TembraIcon.NoteEighthPair, TembraIcon.NoteSixteenthQuad, TembraIcon.NoteEighthTriplet)
        ids.zip(quarter).forEach { (id, icon) ->
            onNodeWithTag("metronome.subdivision.$id.icon.${icon.name}", useUnmergedTree = true).assertExists()
        }
        setUnit(BeatUnit.Eighth)
        val eighth = listOf(TembraIcon.NoteEighth, TembraIcon.NoteSixteenthPair, TembraIcon.NoteThirtySecondQuad, TembraIcon.NoteSixteenthTriplet)
        ids.zip(eighth).forEach { (id, icon) ->
            onNodeWithTag("metronome.subdivision.$id.icon.${icon.name}", useUnmergedTree = true).assertExists()
        }
        onNodeWithTag("metronome.subdivision.1.icon.${TembraIcon.NoteQuarter.name}", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun theSelectedTileIsSelectedAndAClickSelectsAnother() = runComposeUiTest {
        setTiles(selected = Subdivision.Four)
        onNodeWithTag("metronome.subdivision.4").assertIsSelected()
        for (id in listOf("1", "2", "3")) onNodeWithTag("metronome.subdivision.$id").assertIsNotSelected()
        onNodeWithTag("metronome.subdivision.3").performClick()
        waitForIdle()
        assertEquals(listOf(Subdivision.Triplet), chosen)
        // The selected tile selects nothing again.
        onNodeWithTag("metronome.subdivision.4").performClick()
        waitForIdle()
        assertEquals(listOf(Subdivision.Triplet), chosen)
    }

    @Test
    fun hoverShowsTheValueAsATooltip() = runComposeUiTest {
        setTiles()
        onNode(hasText("1/16")).assertDoesNotExist()
        onNodeWithTag("metronome.subdivision.4").performMouseInput { moveTo(center) }
        waitForIdle()
        onNode(hasText("1/16")).assertExists()
    }

    @Test
    fun tabMovesAlongTheTilesAndEnterSelects() = runComposeUiTest {
        setTiles()
        if (!enterKeyboardMode(inputModeManager)) return@runComposeUiTest
        onNodeWithTag("metronome.subdivision.1").requestFocus()
        waitForIdle()
        for (id in ids.drop(1)) {
            onNode(isFocused()).performKeyInput { pressKey(Key.Tab) }
            waitForIdle()
            onNodeWithTag("metronome.subdivision.$id").assertIsFocused()
        }
        onNode(isFocused()).performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals(listOf(Subdivision.Triplet), chosen)
    }
}
