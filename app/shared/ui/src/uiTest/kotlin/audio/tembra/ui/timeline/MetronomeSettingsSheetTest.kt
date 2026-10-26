package audio.tembra.ui.timeline

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import audio.tembra.core.metronome.MetronomeSettings
import audio.tembra.core.metronome.Subdivision
import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.ui.metronome.MetronomeCopy
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.components.METRONOME_SHEET_CELLS_TAG
import audio.tembra.ui.timeline.components.METRONOME_SHEET_SOUND_TAG
import audio.tembra.ui.timeline.components.METRONOME_SHEET_STYLE_TAG
import audio.tembra.ui.timeline.components.METRONOME_SHEET_SUBDIVISION_TAG
import audio.tembra.ui.timeline.components.METRONOME_SHEET_TAG
import audio.tembra.ui.timeline.components.METRONOME_SHEET_TEMPO_TAG
import audio.tembra.ui.timeline.components.MetronomeSettingsSheet
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The transport's metronome sheet (spec FR-037, FR-038, FR-039; T108, US5; D1 T141): the project tempo read-only, the
 * signature chosen there, the four controls as intents.
 */
@OptIn(ExperimentalTestApi::class)
class MetronomeSettingsSheetTest {

    @Test
    fun theSheetShowsTheProjectTempoReadOnlyAndRoutesTheControls() = runComposeUiTest {
        val calls = mutableListOf<String>()
        setContent {
            TembraTheme {
                MetronomeSettingsSheet(
                    settings = MetronomeSettings(), tempo = Tempo(124), signature = TimeSignature.ThreeFour,
                    onTimeSignature = { calls += "signature(${it.label})" },
                    onClickSound = { calls += "sound" }, onImportCustomSound = { calls += "import" },
                    onSubdivision = { calls += "subdivision(${it.name})" }, onCycleBeat = { calls += "cycle($it)" },
                    onStyle = { calls += "style" }, onDismiss = { calls += "dismiss" },
                )
            }
        }
        waitForIdle()
        onNodeWithTag(METRONOME_SHEET_TAG).assertExists()
        onNodeWithTag(METRONOME_SHEET_TEMPO_TAG).assertContentDescriptionEquals("124 BPM, from the project").assertHasNoClickAction()
        onNodeWithTag(METRONOME_SHEET_SOUND_TAG).assertExists()
        onNodeWithTag(METRONOME_SHEET_STYLE_TAG).assertExists()
        onNodeWithTag("$METRONOME_SHEET_SUBDIVISION_TAG.${Subdivision.Two.id}").performClick()
        onNodeWithTag("${METRONOME_SHEET_CELLS_TAG.removeSuffix("s")}.3").performClick()
        assertEquals(listOf("subdivision(Two)", "cycle(2)"), calls)
    }

    /** The signature moved here from the ruler (spec FR-037, D1): the metronome's menu reads the project's and reports a change. */
    @Test
    fun theSignatureMenuReadsTheProjectSignatureAndReportsAChange() = runComposeUiTest {
        val calls = mutableListOf<String>()
        setContent {
            TembraTheme {
                MetronomeSettingsSheet(
                    settings = MetronomeSettings(), tempo = Tempo(124), signature = TimeSignature.ThreeFour,
                    onTimeSignature = { calls += "signature(${it.label})" },
                    onClickSound = {}, onImportCustomSound = {}, onSubdivision = {}, onCycleBeat = {}, onStyle = {}, onDismiss = {},
                )
            }
        }
        waitForIdle()
        onNodeWithTag(TimelineTestTags.METRONOME_SHEET_SIGNATURE).assertContentDescriptionEquals(MetronomeCopy.timeSignatureOf("3/4")).assertHasClickAction().performClick()
        onNodeWithTag("${TimelineTestTags.METRONOME_SHEET_SIGNATURE}.${TimeSignature.SixEight.id}").performClick()
        assertEquals(listOf("signature(6/8)"), calls)
        // The cells follow the project's signature: three at 3/4.
        onNodeWithTag("${METRONOME_SHEET_CELLS_TAG.removeSuffix("s")}.3").assertExists()
        onNodeWithTag("${METRONOME_SHEET_CELLS_TAG.removeSuffix("s")}.4").assertDoesNotExist()
    }
}
