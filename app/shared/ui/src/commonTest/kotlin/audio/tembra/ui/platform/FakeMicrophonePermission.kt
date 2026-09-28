package audio.tembra.ui.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Scripted permission double for ViewModel and screen tests (contracts/microphone-permission.md). */
class FakeMicrophonePermission(initial: MicrophonePermissionStatus) : MicrophonePermission {
    private val _status = MutableStateFlow(initial)
    override val status: StateFlow<MicrophonePermissionStatus> = _status.asStateFlow()

    /** Results returned by successive [request] calls; when empty, the current status is returned unchanged. */
    val requestResults = ArrayDeque<MicrophonePermissionStatus>()
    var requestCount = 0
        private set
    var refreshCount = 0
        private set
    var openSettingsCount = 0
        private set

    override suspend fun request(): MicrophonePermissionStatus {
        requestCount++
        val result = requestResults.removeFirstOrNull() ?: _status.value
        _status.value = result
        return result
    }

    override fun refresh() {
        refreshCount++
    }

    override fun openSystemSettings() {
        openSettingsCount++
    }

    /** Simulates a change made outside the app (system settings, a revoke). */
    fun set(status: MicrophonePermissionStatus) {
        _status.value = status
    }
}
