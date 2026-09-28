package audio.tembra.engine

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionInterruptionOptionKey
import platform.AVFAudio.AVAudioSessionInterruptionOptionShouldResume
import platform.AVFAudio.AVAudioSessionInterruptionTypeBegan
import platform.AVFAudio.AVAudioSessionInterruptionTypeEnded
import platform.AVFAudio.AVAudioSessionInterruptionTypeKey
import platform.AVFAudio.AVAudioSessionModeDefault
import platform.AVFAudio.AVAudioSessionModeMeasurement
import platform.AVFAudio.AVAudioSessionRouteChangeNotification
import platform.AVFAudio.AVAudioSessionRouteChangeReasonKey
import platform.AVFAudio.AVAudioSessionRouteChangeReasonOldDeviceUnavailable
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
import platform.AVFAudio.setActive
import platform.AVFAudio.setPreferredIOBufferDuration
import platform.AVFAudio.setPreferredSampleRate
import platform.Foundation.NSError
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber

/**
 * The AVAudioSession around the engine's streams (research R3, R7): measurement mode keeps processing off the
 * microphone, playback category routes tones with other media, and deactivating with `notifyOthersOnDeactivation`
 * lets other apps' audio resume. Interruptions and route changes surface as [AudioEvent]s.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
object IosAudioSession {
    private const val PREFERRED_SAMPLE_RATE = 48000.0
    private const val PREFERRED_IO_BUFFER_SECONDS = 0.005

    private val _events = MutableSharedFlow<AudioEvent>(extraBufferCapacity = 16)
    val events: Flow<AudioEvent> = _events

    private val session: AVAudioSession get() = AVAudioSession.sharedInstance()

    init {
        val center = NSNotificationCenter.defaultCenter
        center.addObserverForName(AVAudioSessionInterruptionNotification, `object` = null, queue = null) { notification ->
            onInterruption(notification)
        }
        center.addObserverForName(AVAudioSessionRouteChangeNotification, `object` = null, queue = null) { notification ->
            onRouteChange(notification)
        }
    }

    private inline fun withError(block: (kotlinx.cinterop.CPointer<ObjCObjectVar<NSError?>>) -> Boolean): Boolean = memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        val ok = block(error.ptr)
        ok && error.value == null
    }

    /** `playAndRecord` in measurement mode at 48 kHz with a 5 ms buffer; true when the session is active. */
    fun activateForListening(): Boolean {
        val category = withError { session.setCategory(AVAudioSessionCategoryPlayAndRecord, AVAudioSessionModeMeasurement, 0uL, it) }
        withError { session.setPreferredSampleRate(PREFERRED_SAMPLE_RATE, it) }
        withError { session.setPreferredIOBufferDuration(PREFERRED_IO_BUFFER_SECONDS, it) }
        val active = withError { session.setActive(true, it) }
        return category && active
    }

    /** `playback` in the default mode; true when the session is active. */
    fun activateForPlayback(): Boolean {
        val category = withError { session.setCategory(AVAudioSessionCategoryPlayback, AVAudioSessionModeDefault, 0uL, it) }
        val active = withError { session.setActive(true, it) }
        return category && active
    }

    /** Deactivates and tells other apps they may resume; true on success. */
    fun deactivate(): Boolean = withError { session.setActive(false, AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation, it) }

    private fun onInterruption(notification: NSNotification?) {
        val info = notification?.userInfo ?: return
        val type = (info[AVAudioSessionInterruptionTypeKey] as? NSNumber)?.unsignedLongValue ?: return
        when (type) {
            AVAudioSessionInterruptionTypeBegan -> _events.tryEmit(AudioEvent.Interrupted)
            AVAudioSessionInterruptionTypeEnded -> {
                val options = (info[AVAudioSessionInterruptionOptionKey] as? NSNumber)?.unsignedLongValue ?: 0uL
                if (options and AVAudioSessionInterruptionOptionShouldResume != 0uL) _events.tryEmit(AudioEvent.InterruptionEnded)
            }
        }
    }

    private fun onRouteChange(notification: NSNotification?) {
        val info = notification?.userInfo ?: return
        val reason = (info[AVAudioSessionRouteChangeReasonKey] as? NSNumber)?.unsignedLongValue ?: return
        if (reason == AVAudioSessionRouteChangeReasonOldDeviceUnavailable) _events.tryEmit(AudioEvent.OutputRouteLost)
    }
}
