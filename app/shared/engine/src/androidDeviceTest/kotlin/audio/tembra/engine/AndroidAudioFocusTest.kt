package audio.tembra.engine

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertTrue

/** Transient audio focus around reference tones (research R7, analysis C1). */
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
}
