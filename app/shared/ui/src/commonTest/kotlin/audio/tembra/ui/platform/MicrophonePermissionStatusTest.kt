package audio.tembra.ui.platform

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The four permission states and the fake that scripts them (data-model.md §5). */
class MicrophonePermissionStatusTest {

    @Test
    fun fourStatesInOrder() {
        assertEquals(
            listOf(
                MicrophonePermissionStatus.NotDetermined,
                MicrophonePermissionStatus.Granted,
                MicrophonePermissionStatus.DeniedCanAsk,
                MicrophonePermissionStatus.DeniedOpenSettings,
            ),
            MicrophonePermissionStatus.entries,
        )
    }

    @Test
    fun onlyTheTwoDeniedValuesAreDenied() {
        assertFalse(MicrophonePermissionStatus.NotDetermined.isDenied)
        assertFalse(MicrophonePermissionStatus.Granted.isDenied)
        assertTrue(MicrophonePermissionStatus.DeniedCanAsk.isDenied)
        assertTrue(MicrophonePermissionStatus.DeniedOpenSettings.isDenied)
    }

    @Test
    fun theFakeConsumesScriptedResults() = runTest {
        val fake = FakeMicrophonePermission(MicrophonePermissionStatus.NotDetermined)
        fake.requestResults += MicrophonePermissionStatus.Granted
        assertEquals(MicrophonePermissionStatus.Granted, fake.request())
        assertEquals(MicrophonePermissionStatus.Granted, fake.status.value)
        assertEquals(1, fake.requestCount)
        assertEquals(MicrophonePermissionStatus.Granted, fake.request())
        assertEquals(2, fake.requestCount)
        fake.refresh()
        fake.openSystemSettings()
        assertEquals(1, fake.refreshCount)
        assertEquals(1, fake.openSettingsCount)
        fake.set(MicrophonePermissionStatus.DeniedOpenSettings)
        assertEquals(MicrophonePermissionStatus.DeniedOpenSettings, fake.status.value)
    }
}
