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
import platform.AVFAudio.AVAudioSessionPortBluetoothA2DP
import platform.AVFAudio.AVAudioSessionPortBluetoothHFP
import platform.AVFAudio.AVAudioSessionPortBluetoothLE
import platform.AVFAudio.AVAudioSessionPortDescription
import platform.AVFAudio.AVAudioSessionPortHeadphones
import platform.AVFAudio.AVAudioSessionRouteChangeNotification
import platform.AVFAudio.AVAudioSessionRouteChangePreviousRouteKey
import platform.AVFAudio.AVAudioSessionRouteChangeReasonKey
import platform.AVFAudio.AVAudioSessionRouteChangeReasonOldDeviceUnavailable
import platform.AVFAudio.AVAudioSessionRouteDescription
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
import platform.AVFAudio.IOBufferDuration
import platform.AVFAudio.outputLatency
import platform.AVFAudio.setActive
import platform.AVFAudio.setPreferredIOBufferDuration
import platform.AVFAudio.setPreferredSampleRate
import platform.Foundation.NSError
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import kotlin.concurrent.Volatile

/**
 * The AVAudioSession around the engine's streams (research R3, R7): measurement mode keeps processing off the
 * microphone, playback category routes tones with other media, and deactivating with `notifyOthersOnDeactivation`
 * lets other apps' audio resume. Interruptions and route changes surface as [AudioEvent]s; losing headphones or a
 * Bluetooth output is [AudioEvent.OutputRouteLost] (feature 004 R6).
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
object IosAudioSession {
    private const val PREFERRED_SAMPLE_RATE = 48000.0
    private const val PREFERRED_IO_BUFFER_SECONDS = 0.005
    private const val NANOS_PER_SECOND = 1_000_000_000.0

    private val _events = MutableSharedFlow<AudioEvent>(extraBufferCapacity = 16)
    val events: Flow<AudioEvent> = _events

    /** Ports whose loss means the listener took the audio off (Apple: stop playback when headphones are unplugged). */
    private val privateOutputs = setOf(
        AVAudioSessionPortHeadphones,
        AVAudioSessionPortBluetoothA2DP,
        AVAudioSessionPortBluetoothLE,
        AVAudioSessionPortBluetoothHFP,
    )

    private val session: AVAudioSession get() = AVAudioSession.sharedInstance()

    /**
     * Called on every route change, on the notification's thread; the engine sets it while the metronome runs to
     * re-report the output latency of the new route (feature 004 R7) and clears it on stop.
     */
    @Volatile
    internal var routeChangeListener: (() -> Unit)? = null

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

    /** The active route's output latency plus one IO buffer, in nanoseconds: what the listener hears late by. */
    fun outputLatencyNanos(): Long = ((session.outputLatency + session.IOBufferDuration) * NANOS_PER_SECOND).toLong()

    /** Deactivates and tells other apps they may resume; true on success. */
    fun deactivate(): Boolean = withError { session.setActive(false, AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation, it) }

    private fun onInterruption(notification: NSNotification?) {
        val info = notification?.userInfo ?: return
        val type = (info[AVAudioSessionInterruptionTypeKey] as? NSNumber)?.unsignedLongValue ?: return
        val options = (info[AVAudioSessionInterruptionOptionKey] as? NSNumber)?.unsignedLongValue ?: 0uL
        handleInterruption(type, options)
    }

    /** The interruption handler on parsed values; internal so tests need no real notification. */
    internal fun handleInterruption(type: ULong, options: ULong) {
        when (type) {
            AVAudioSessionInterruptionTypeBegan -> _events.tryEmit(AudioEvent.Interrupted)
            AVAudioSessionInterruptionTypeEnded ->
                if (options and AVAudioSessionInterruptionOptionShouldResume != 0uL) _events.tryEmit(AudioEvent.InterruptionEnded)
        }
    }

    private fun onRouteChange(notification: NSNotification?) {
        val info = notification?.userInfo ?: return
        val reason = (info[AVAudioSessionRouteChangeReasonKey] as? NSNumber)?.unsignedLongValue ?: return
        val previous = info[AVAudioSessionRouteChangePreviousRouteKey] as? AVAudioSessionRouteDescription
        val previousOutputs = previous?.outputs.orEmpty().mapNotNull { (it as? AVAudioSessionPortDescription)?.portType }
        handleRouteChange(reason, previousOutputs)
    }

    /**
     * The route-change handler on parsed values ([previousOutputs] are the previous route's output port types);
     * internal so tests need no real notification. Only a lost headphone or Bluetooth output is reported: a new device
     * or a lost speaker keeps the audio going.
     */
    internal fun handleRouteChange(reason: ULong, previousOutputs: List<String>) {
        if (reason == AVAudioSessionRouteChangeReasonOldDeviceUnavailable && previousOutputs.any { it in privateOutputs }) {
            _events.tryEmit(AudioEvent.OutputRouteLost)
        }
        routeChangeListener?.invoke()
    }
}
