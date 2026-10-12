package audio.tembra.ui.perf

import audio.tembra.ui.metronome.MetronomeUiState
import audio.tembra.ui.tuner.TunerUiState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The predicate behind `ReportFirstContentDrawn` (spec FR-017): a screen is "fully drawn" once its first content
 * state has loaded, never while it still shows nothing.
 */
class FirstContentReadyTest {

    @Test
    fun tunerIsReadyOnceLoaded() {
        assertFalse(TunerUiState().isFirstContentReady)
        assertTrue(TunerUiState(loaded = true).isFirstContentReady)
    }

    @Test
    fun metronomeIsReadyOnceLoaded() {
        assertFalse(MetronomeUiState().isFirstContentReady)
        assertTrue(MetronomeUiState(loaded = true).isFirstContentReady)
    }
}
