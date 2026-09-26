package audio.tembra.ui.platform

import kotlinx.coroutines.flow.StateFlow

/**
 * Microphone access behind an interface (research R6): the Android implementation lives in `:androidApp` (it needs
 * the activity result registry), the iOS one in this module's `iosMain`; tests use `FakeMicrophonePermission`.
 */
interface MicrophonePermission {
    val status: StateFlow<MicrophonePermissionStatus>

    /** Shows the OS prompt when the OS allows and resolves with the new status. */
    suspend fun request(): MicrophonePermissionStatus

    /** Re-reads the OS state; called on every resume so a grant made in the settings is noticed. */
    fun refresh()

    /** Opens the app's page in the system settings. */
    fun openSystemSettings()
}
