package audio.tembra.ui.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionRecordPermissionDenied
import platform.AVFAudio.AVAudioSessionRecordPermissionGranted
import platform.AVFAudio.AVAudioSessionRecordPermissionUndetermined
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import kotlin.coroutines.resume

/**
 * Microphone access through `AVAudioSession` (research R6). iOS never re-prompts, so a denial is always
 * [MicrophonePermissionStatus.DeniedOpenSettings]. The deployment target is iOS 16, hence `AVAudioSession` rather than
 * iOS 17's `AVAudioApplication`, which replaces `recordPermission` and `requestRecordPermission` once the target moves.
 */
class IosMicrophonePermission : MicrophonePermission {
    private val _status = MutableStateFlow(read())
    override val status: StateFlow<MicrophonePermissionStatus> = _status.asStateFlow()

    override suspend fun request(): MicrophonePermissionStatus {
        if (read() != MicrophonePermissionStatus.NotDetermined) {
            refresh()
            return _status.value
        }
        suspendCancellableCoroutine { continuation ->
            AVAudioSession.sharedInstance().requestRecordPermission { _ ->
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
        refresh()
        return _status.value
    }

    override fun refresh() {
        _status.value = read()
    }

    override fun openSystemSettings() {
        val url = NSURL(string = UIApplicationOpenSettingsURLString)
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
    }

    private fun read(): MicrophonePermissionStatus = when (AVAudioSession.sharedInstance().recordPermission) {
        AVAudioSessionRecordPermissionGranted -> MicrophonePermissionStatus.Granted
        AVAudioSessionRecordPermissionDenied -> MicrophonePermissionStatus.DeniedOpenSettings
        AVAudioSessionRecordPermissionUndetermined -> MicrophonePermissionStatus.NotDetermined
        else -> MicrophonePermissionStatus.NotDetermined
    }
}
