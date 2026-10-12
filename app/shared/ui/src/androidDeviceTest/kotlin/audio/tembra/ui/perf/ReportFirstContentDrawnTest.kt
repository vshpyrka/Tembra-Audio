package audio.tembra.ui.perf

import androidx.activity.FullyDrawnReporter
import androidx.activity.FullyDrawnReporterOwner
import androidx.activity.compose.LocalFullyDrawnReporterOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import audio.tembra.data.metronome.FakeCustomClickSoundStore
import audio.tembra.data.metronome.FakeMetronomePreferences
import audio.tembra.data.tuner.FakeTunerPreferences
import audio.tembra.engine.FakeAudioEngine
import audio.tembra.ui.home.HomeScreen
import audio.tembra.ui.metronome.MetronomeScreen
import audio.tembra.ui.metronome.MetronomeViewModel
import audio.tembra.ui.platform.FakeBackgroundPlaybackHost
import audio.tembra.ui.platform.FakeMicrophonePermission
import audio.tembra.ui.platform.FakeSoundFilePicker
import audio.tembra.ui.platform.MicrophonePermissionStatus
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.tuner.ReferenceSoundRepository
import audio.tembra.ui.tuner.TunerScreen
import audio.tembra.ui.tuner.TunerViewModel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers

/**
 * The Android actual of `ReportFirstContentDrawn` reports "fully drawn" to the Activity's reporter (what
 * Macrobenchmark's `timeToFullDisplayMs` measures) exactly when the predicate turns true, and the Home, Tuner and
 * Metronome wiring composables hand it their first-content state (spec FR-017).
 */
@OptIn(ExperimentalTestApi::class)
class ReportFirstContentDrawnTest {

    /** A reporter outside any Activity: `reportFullyDrawn` flips [reported]. */
    private class Owner : FullyDrawnReporterOwner {
        var reported = false
        override val fullyDrawnReporter = FullyDrawnReporter({ it.run() }) { reported = true }
    }

    @Test
    fun reportsOnlyOnceThePredicateIsTrue() = runComposeUiTest {
        val owner = Owner()
        var ready by mutableStateOf(false)
        setContent {
            CompositionLocalProvider(LocalFullyDrawnReporterOwner provides owner) {
                ReportFirstContentDrawn(ready)
            }
        }
        waitForIdle()
        assertFalse(owner.reported)
        ready = true
        waitForIdle()
        assertTrue(owner.reported)
    }

    @Test
    fun homeReportsAtOnce() = runComposeUiTest {
        val owner = Owner()
        setContent {
            CompositionLocalProvider(LocalFullyDrawnReporterOwner provides owner) {
                TembraTheme { HomeScreen() }
            }
        }
        waitForIdle()
        assertTrue(owner.reported)
    }

    @Test
    fun tunerReportsOnceItsStateHasLoaded() = runComposeUiTest {
        val owner = Owner()
        val viewModel = TunerViewModel(
            FakeAudioEngine(),
            FakeMicrophonePermission(MicrophonePermissionStatus.Granted),
            FakeTunerPreferences(),
            ReferenceSoundRepository { null },
            Dispatchers.Default,
        )
        setContent {
            CompositionLocalProvider(LocalFullyDrawnReporterOwner provides owner) {
                TembraTheme { TunerScreen(viewModel = viewModel) }
            }
        }
        waitUntil(timeoutMillis = 5_000) { viewModel.uiState.value.loaded }
        waitForIdle()
        assertTrue(owner.reported)
    }

    @Test
    fun metronomeReportsOnceItsStateHasLoaded() = runComposeUiTest {
        val owner = Owner()
        val store = FakeCustomClickSoundStore()
        val viewModel = MetronomeViewModel(
            FakeAudioEngine(),
            FakeBackgroundPlaybackHost(),
            FakeMetronomePreferences(),
            store,
            FakeSoundFilePicker(files = store.files),
            Dispatchers.Default,
        )
        setContent {
            CompositionLocalProvider(LocalFullyDrawnReporterOwner provides owner) {
                TembraTheme { MetronomeScreen(viewModel = viewModel) }
            }
        }
        waitUntil(timeoutMillis = 5_000) { viewModel.uiState.value.loaded }
        waitForIdle()
        assertTrue(owner.reported)
    }
}
