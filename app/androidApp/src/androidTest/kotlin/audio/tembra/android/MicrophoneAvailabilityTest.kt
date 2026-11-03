package audio.tembra.android

import androidx.test.platform.app.InstrumentationRegistry
import audio.tembra.ui.platform.MicrophonePermissionStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * A desktop without a microphone (feature 011, spec FR-002, research R2): with no input device the permission reports
 * [MicrophonePermissionStatus.Unavailable] and never shows the prompt; with one it reports the permission as before.
 * The device's audio inputs are faked through [MicrophoneAvailability]: the emulators always report a microphone.
 */
class MicrophoneAvailabilityTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun noInputDeviceIsUnavailableAndNeverPrompts() = runBlocking {
        var prompts = 0
        val permission = AndroidMicrophonePermission(context, MicrophonePermissionHost { prompts++ }) { false }
        assertEquals(MicrophonePermissionStatus.Unavailable, permission.status.value)
        assertEquals(MicrophonePermissionStatus.Unavailable, permission.request())
        assertEquals(0, prompts)
    }

    @Test
    fun aMicrophoneArrivingIsNoticedOnTheNextRefresh() {
        var present = false
        val permission = AndroidMicrophonePermission(context, MicrophonePermissionHost { }) { present }
        assertEquals(MicrophonePermissionStatus.Unavailable, permission.status.value)
        present = true
        permission.refresh()
        assertNotEquals(MicrophonePermissionStatus.Unavailable, permission.status.value)
    }

    @Test
    fun theDevicesOwnInputsAreReadFromTheAudioManager() {
        // Every target in the lanes (phones, tablets, the desktop emulators) has at least one microphone.
        assertEquals(true, AndroidMicrophoneAvailability(context).hasInput())
    }
}
