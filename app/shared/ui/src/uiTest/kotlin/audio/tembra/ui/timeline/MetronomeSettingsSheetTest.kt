package audio.tembra.ui.timeline

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import audio.tembra.core.metronome.MetronomeSettings
import audio.tembra.core.metronome.Subdivision
import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
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

/** The transport's metronome sheet (spec FR-038, FR-039; T108, US5): read-only tempo and signature, the four controls as intents. */
@OptIn(ExperimentalTestApi::class)
class MetronomeSettingsSheetTest {

    @Test
    fun theSheetShowsTheProjectTempoReadOnlyAndRoutesTheControls() = runComposeUiTest {
        val calls = mutableListOf<String>()
        setContent {
            TembraTheme {
                MetronomeSettingsSheet(
                    settings = MetronomeSettings(), tempo = Tempo(124), signature = TimeSignature.ThreeFour,
                    onClickSound = { calls += "sound" }, onImportCustomSound = { calls += "import" },
                    onSubdivision = { calls += "subdivision(${it.name})" }, onCycleBeat = { calls += "cycle($it)" },
                    onStyle = { calls += "style" }, onDismiss = { calls += "dismiss" },
                )
            }
        }
        waitForIdle()
        onNodeWithTag(METRONOME_SHEET_TAG).assertExists()
        onNodeWithTag(METRONOME_SHEET_TEMPO_TAG).assertContentDescriptionEquals("124 BPM, 3/4")
        onNodeWithTag(METRONOME_SHEET_SOUND_TAG).assertExists()
        onNodeWithTag(METRONOME_SHEET_STYLE_TAG).assertExists()
        onNodeWithTag("$METRONOME_SHEET_SUBDIVISION_TAG.${Subdivision.Two.id}").performClick()
        onNodeWithTag("${METRONOME_SHEET_CELLS_TAG.removeSuffix("s")}.3").performClick()
        assertEquals(listOf("subdivision(Two)", "cycle(2)"), calls)
    }
}
