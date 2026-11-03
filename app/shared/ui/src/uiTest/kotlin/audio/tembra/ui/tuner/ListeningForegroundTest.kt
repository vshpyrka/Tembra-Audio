package audio.tembra.ui.tuner

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.LocalLifecycleOwner
import audio.tembra.data.tuner.FakeTunerPreferences
import audio.tembra.engine.AudioState
import audio.tembra.engine.FakeAudioEngine
import audio.tembra.ui.platform.FakeMicrophonePermission
import audio.tembra.ui.platform.MicrophonePermissionStatus
import audio.tembra.ui.theme.TembraTheme
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What a desktop window's focus and minimise do to Auto Listen (feature 011, spec FR-006/FR-009; contracts/window-
 * behaviour.md): the screen follows `ON_START`/`ON_STOP`, not pause. Another window taking focus pauses at most (on
 * iOS a resign-active is `ON_PAUSE`), so listening goes on; minimising stops the activity or sends the scene to the
 * background (`ON_STOP`), so listening stops; restoring starts it again. Driven through the real [TunerScreen] and a
 * lifecycle owner the test moves.
 */
@OptIn(ExperimentalTestApi::class)
class ListeningForegroundTest {

    private class TestOwner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun pauseKeepsListeningStopStopsAndStartResumes() = runComposeUiTest {
        val audio = FakeAudioEngine()
        val viewModel = TunerViewModel(
            audio,
            FakeMicrophonePermission(MicrophonePermissionStatus.Granted),
            FakeTunerPreferences(),
            ReferenceSoundRepository { null },
            Dispatchers.Unconfined,
            Dispatchers.Unconfined,
        )
        val owner = TestOwner()
        runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                TembraTheme { TunerScreen(viewModel = viewModel) }
            }
        }
        waitUntil(timeoutMillis = 5_000) { audio.audioState == AudioState.Listening }

        // Another window takes focus: pause only.
        runOnUiThread { owner.registry.currentState = Lifecycle.State.STARTED }
        waitForIdle()
        assertEquals(AudioState.Listening, audio.audioState, "losing focus stopped listening")

        // Minimised: stopped.
        runOnUiThread { owner.registry.currentState = Lifecycle.State.CREATED }
        waitUntil(timeoutMillis = 5_000) { audio.audioState == AudioState.Idle }

        // Restored: started and resumed again.
        runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        waitUntil(timeoutMillis = 5_000) { audio.audioState == AudioState.Listening }

        runOnUiThread { ViewModelStore().apply { put("tuner", viewModel) }.clear() }
    }
}
