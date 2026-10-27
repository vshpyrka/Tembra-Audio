package audio.tembra.android

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Android route binding behind the shared interface (feature 010, research R12; T022). */
class AndroidAudioRouteMonitorTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** On the bench nothing but the USB cable is attached: the output is the speaker and the input the built-in microphone. */
    @Test
    fun reportsTheSpeakerAndTheBuiltInMicrophoneWhenNothingIsConnected() {
        val monitor = AndroidAudioRouteMonitor(context)
        val route = monitor.route.value
        assertTrue("the output should be the built-in speaker, was ${route.outputName}", route.outputIsSpeaker)
        assertEquals("Speaker", route.outputName)
        assertTrue("an input id is reported", route.inputId.isNotEmpty())
        assertTrue(route.inputName.isNotEmpty())
    }
}
