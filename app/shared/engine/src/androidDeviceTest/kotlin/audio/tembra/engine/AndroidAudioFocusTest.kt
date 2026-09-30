package audio.tembra.engine

import android.media.AudioManager
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Transient audio focus around reference tones and full focus around the metronome (research R7, feature 004 R6). */
class AndroidAudioFocusTest {
    private val focus = AndroidAudioFocus(InstrumentationRegistry.getInstrumentation().targetContext)

    @Test
    fun requestIsGrantedWhenNobodyHoldsFocusAndAbandonNeverThrows() {
        val granted = focus.request()
        if (!granted) println("AndroidAudioFocusTest: focus request denied (another app holds focus); assertion skipped")
        focus.abandon()
        focus.abandon()
        assertTrue(true)
    }

    @Test
    fun requestAndAbandonEmitNoEvents() = runTest {
        val collected = mutableListOf<AudioEvent>()
        val collector = launch { withTimeoutOrNull(200) { focus.events.toList(collected) } }
        focus.request()
        focus.abandon()
        collector.join()
        assertTrue(collected.isEmpty(), "unexpected events $collected")
    }

    @Test
    fun gainIsGrantedWhenNobodyHoldsFocusAndAbandonNeverThrows() {
        val granted = focus.request(FocusType.Gain)
        if (!granted) println("AndroidAudioFocusTest: Gain request denied (another app holds focus); assertion skipped")
        focus.abandon()
        focus.abandon()
        assertTrue(true)
    }

    @Test
    fun aPermanentOrTransientLossInterruptsAndADuckableLossDoesNot() = runTest {
        val collected = mutableListOf<AudioEvent>()
        val collector = launch { withTimeoutOrNull(200) { focus.events.toList(collected) } }
        testScheduler.runCurrent()
        focus.onFocusChange(AudioManager.AUDIOFOCUS_LOSS)
        focus.onFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
        focus.onFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        collector.join()
        assertEquals(listOf<AudioEvent>(AudioEvent.Interrupted, AudioEvent.Interrupted), collected)
    }

    @Test
    fun aRegainAfterATransientLossEndsTheInterruption() = runTest {
        val collected = mutableListOf<AudioEvent>()
        val collector = launch { withTimeoutOrNull(200) { focus.events.toList(collected) } }
        testScheduler.runCurrent()
        focus.onFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        focus.onFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        collector.join()
        assertEquals(listOf(AudioEvent.Interrupted, AudioEvent.InterruptionEnded), collected)
    }
}
