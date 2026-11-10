package audio.tembra.ui.mixer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import audio.tembra.core.project.GainDb
import audio.tembra.core.project.Pan
import audio.tembra.core.project.TrackId
import audio.tembra.ui.session.InputTelemetry
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.TimelineCallbacks
import audio.tembra.ui.timeline.TimelineLayout
import kotlinx.collections.immutable.toPersistentList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The strip row with many tracks (feature 012, spec US6, FR-001, FR-024; T095): 24 strips scroll end to end with the
 * master pinned at the far end, and the items are keyed by track id, so a removed middle track leaves the others
 * where they were (the row keeps the strip it was scrolled to). The live meters change no composition: that is the
 * compiler report of T097 (`evidence/compose-report/`), since a recomposition counter needs a hook the production code
 * does not expose; the per-frame read with 24 strips is `MixerSheetTest`'s.
 */
@OptIn(ExperimentalTestApi::class)
class MixerStripRowTest {

    private val calls = mutableListOf<String>()
    private val ui = mutableStateOf(MixerUi())
    private var pageMargin = 0.dp
    private val callbacks = TimelineCallbacks.None.copy(onStripSelect = { calls += "select(${it.value})" })

    private fun strips(count: Int) = List(count) { index ->
        StripUi(
            id = TrackId("t${index + 1}"), index = index, number = index + 1, name = "Track ${index + 1}", colorIndex = index % 8,
            gainDb = GainDb.Unity, pan = Pan.Centre, muted = false, soloed = false, audible = true,
        )
    }.toPersistentList()

    /** The half sheet with [count] strips in a 360 dp window over 500 dp of lanes. */
    private fun ComposeUiTest.setSheet(count: Int = 24) {
        val anchors = MixerAnchors.of(TimelineLayout.Compact, LANES)
        ui.value = MixerUi(open = true, height = MixerHeight.Half, form = StripForm.Compact, anchors = anchors, strips = strips(count))
        setContent {
            TembraTheme {
                pageMargin = TembraTheme.spacing.pageMargin
                Box(Modifier.requiredWidth(WIDTH).requiredHeight(LANES)) {
                    MixerSheet(ui = ui.value, callbacks = callbacks, telemetry = ScriptedMixTelemetry.silent(count), inputTelemetry = InputTelemetry { _, _ -> }, lanesHeight = LANES)
                }
            }
        }
        waitForIdle()
    }

    /** Removes the strip at [index], the rest re-indexed as the ViewModel would (the ids stay). */
    private fun removeStrip(index: Int) {
        val kept = ui.value.strips.filterIndexed { i, _ -> i != index }
        ui.value = ui.value.copy(strips = kept.mapIndexed { i, strip -> strip.copy(index = i, number = i + 1) }.toPersistentList())
    }

    /** The track name of the first strip inside the row's bounds, in whole or in part (by bounds, not by the screen). */
    private fun ComposeUiTest.firstVisibleName(): String {
        val row = onNodeWithTag(MixerTestTags.STRIPS).getUnclippedBoundsInRoot()
        val index = ui.value.strips.indices.first { i ->
            val tag = MixerTestTags.strip(i)
            onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() && onNodeWithTag(tag).getUnclippedBoundsInRoot().right > row.left
        }
        return ui.value.strips[index].name
    }

    @Test
    fun twentyFourStripsScrollToTheLastWithTheMasterPinned() = runComposeUiTest {
        setSheet()
        val sheet = onNodeWithTag(MixerTestTags.SHEET).getUnclippedBoundsInRoot()
        val master = onNodeWithTag(MixerTestTags.MASTER).getUnclippedBoundsInRoot()
        assertTrue(master.right >= sheet.right - pageMargin - 1.dp, "the master $master is not pinned at the sheet's right edge $sheet")
        onNodeWithTag(MixerTestTags.STRIPS).performScrollToNode(hasTestTag(MixerTestTags.strip(23)))
        waitForIdle()
        onNodeWithTag(MixerTestTags.strip(23)).assertIsDisplayed()
        onNodeWithTag(MixerTestTags.name(23)).assertTextEquals("Track 24")
        assertEquals(master, onNodeWithTag(MixerTestTags.MASTER).getUnclippedBoundsInRoot(), "the master moved with the scroll")
        onNodeWithTag(MixerTestTags.MASTER_FADER).assertIsDisplayed()
        assertEquals(emptyList(), calls)
    }

    @Test
    fun aRemovedMiddleTrackLeavesTheOthersWhereTheyWere() = runComposeUiTest {
        setSheet()
        onNodeWithTag(MixerTestTags.STRIPS).performScrollToNode(hasTestTag(MixerTestTags.strip(12)))
        waitForIdle()
        val shown = firstVisibleName()
        assertTrue(shown != "Track 1", "the row did not scroll")
        removeStrip(3)
        waitForIdle()
        // Keyed by id: the strips the row showed keep their place; the tracks after the removed one moved up by one index.
        assertEquals(23, ui.value.strips.size)
        assertEquals(shown, firstVisibleName(), "the first visible strip after the removal")
        onNodeWithTag(MixerTestTags.STRIPS).performScrollToNode(hasTestTag(MixerTestTags.strip(3)))
        onNodeWithTag(MixerTestTags.name(3)).assertTextEquals("Track 5")
    }

    private companion object {
        val WIDTH = 360.dp
        val LANES = 500.dp
    }
}
