package audio.tembra.android

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import audio.tembra.ui.platform.MicrophonePermission
import audio.tembra.ui.platform.MicrophonePermissionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Microphone access on Android (research R6): `checkSelfPermission` for the state, the activity's request launcher
 * through [MicrophonePermissionHost] for the prompt, and the app's details page in the system settings once the OS
 * stops asking. A device with no microphone ([availability], feature 011) is
 * [MicrophonePermissionStatus.Unavailable] before any of that, and is never prompted.
 */
class AndroidMicrophonePermission(
    private val context: Context,
    private val host: MicrophonePermissionHost,
    private val availability: MicrophoneAvailability = AndroidMicrophoneAvailability(context),
) : MicrophonePermission {
    private val _status = MutableStateFlow(read())
    override val status: StateFlow<MicrophonePermissionStatus> = _status.asStateFlow()

    override suspend fun request(): MicrophonePermissionStatus {
        refresh()
        if (_status.value == MicrophonePermissionStatus.Unavailable) return _status.value
        host.launch()
        refresh()
        return _status.value
    }

    override fun refresh() {
        _status.value = read()
    }

    override fun openSystemSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun read(): MicrophonePermissionStatus = when {
        !availability.hasInput() -> MicrophonePermissionStatus.Unavailable
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> MicrophonePermissionStatus.Granted
        host.shouldShowRationale() -> MicrophonePermissionStatus.DeniedCanAsk
        host.hasAsked() -> MicrophonePermissionStatus.DeniedOpenSettings
        else -> MicrophonePermissionStatus.NotDetermined
    }
}
