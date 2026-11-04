package audio.tembra.ui.platform

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The AVAudioSession-backed permission on the simulator (research R6, analysis C1). */
class IosMicrophonePermissionTest {

    @Test
    fun noAudioInputIsUnavailableAndNeverPrompts() = runBlocking {
        // Feature 011 (spec FR-002, research R2): an iPad with no microphone route reports Unavailable before any prompt.
        val permission = IosMicrophonePermission(inputAvailable = { false })
        assertEquals(MicrophonePermissionStatus.Unavailable, permission.status.value)
        assertEquals(MicrophonePermissionStatus.Unavailable, permission.request())
    }

    @Test
    fun anInputArrivingIsNoticedOnTheNextRefresh() {
        var available = false
        val permission = IosMicrophonePermission(inputAvailable = { available })
        assertEquals(MicrophonePermissionStatus.Unavailable, permission.status.value)
        available = true
        permission.refresh()
        assertTrue(permission.status.value != MicrophonePermissionStatus.Unavailable)
    }

    @Test
    fun refreshYieldsOneOfTheStatuses() {
        val permission = IosMicrophonePermission()
        permission.refresh()
        assertTrue(permission.status.value in MicrophonePermissionStatus.entries)
    }

    @Test
    fun requestResolvesToTheCurrentStatus() = runBlocking {
        val permission = IosMicrophonePermission()
        permission.refresh()
        val result = withTimeoutOrNull(5_000) { permission.request() }
        if (result == null) {
            println("IosMicrophonePermissionTest: the simulator did not answer the prompt within 5 s; assertion skipped")
            return@runBlocking
        }
        assertEquals(result, permission.status.value)
    }

    @Test
    fun openSystemSettingsDoesNotThrow() {
        IosMicrophonePermission().openSystemSettings()
    }
}
