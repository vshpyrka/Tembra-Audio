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
import platform.AVFAudio.AVAudioSessionCategoryOptionAllowBluetoothA2DP
import platform.AVFAudio.AVAudioSessionCategoryOptionDefaultToSpeaker
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
import platform.AVFAudio.AVAudioSessionPortBuiltInReceiver
import platform.AVFAudio.AVAudioSessionPortBuiltInSpeaker
import platform.AVFAudio.AVAudioSessionPortDescription
import platform.AVFAudio.AVAudioSessionRouteChangeNotification
import platform.AVFAudio.AVAudioSessionRouteChangePreviousRouteKey
import platform.AVFAudio.AVAudioSessionRouteChangeReasonKey
import platform.AVFAudio.AVAudioSessionRouteChangeReasonOldDeviceUnavailable
import platform.AVFAudio.AVAudioSessionRouteDescription
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
import platform.AVFAudio.IOBufferDuration
import platform.AVFAudio.currentRoute
import platform.AVFAudio.inputLatency
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
 * lets other apps' audio resume. Interruptions and route changes surface as [AudioEvent]s; losing any output but the
 * built-in speaker or receiver (wired, USB-C, Bluetooth, AirPlay) is [AudioEvent.OutputRouteLost] (feature 004 R6).
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
object IosAudioSession {
    private const val PREFERRED_SAMPLE_RATE = 48000.0
    private const val PREFERRED_IO_BUFFER_SECONDS = 0.005
    private const val NANOS_PER_SECOND = 1_000_000_000.0

    private val _events = MutableSharedFlow<AudioEvent>(extraBufferCapacity = 16)
    val events: Flow<AudioEvent> = _events

    /**
     * The phone's own outputs. Apple keys "stop playback when the old device became unavailable" on the reason alone, so
     * losing any other output (wired, USB-C, Bluetooth, AirPlay, CarPlay, line out) takes the audio off the listener.
     */
    private val builtInOutputs = setOf(
        AVAudioSessionPortBuiltInSpeaker,
        AVAudioSessionPortBuiltInReceiver,
    )

    private val session: AVAudioSession get() = AVAudioSession.sharedInstance()

    /** True between a successful activation and a successful deactivation; internal so tests can observe it. */
    @Volatile
    internal var isActive: Boolean = false
        private set

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
        if (active) isActive = true
        return category && active
    }

    /** `playback` in the default mode; true when the session is active. */
    fun activateForPlayback(): Boolean {
        val category = withError { session.setCategory(AVAudioSessionCategoryPlayback, AVAudioSessionModeDefault, 0uL, it) }
        val active = withError { session.setActive(true, it) }
        if (active) isActive = true
        return category && active
    }

    /** The active route's output latency plus one IO buffer, in nanoseconds: what the listener hears late by. */
    fun outputLatencyNanos(): Long = ((session.outputLatency + session.IOBufferDuration) * NANOS_PER_SECOND).toLong()

    /** The active route's input latency plus one IO buffer, in nanoseconds (feature 010, research R9). */
    fun inputLatencyNanos(): Long = ((session.inputLatency + session.IOBufferDuration) * NANOS_PER_SECOND).toLong()

    /**
     * `playAndRecord` in measurement mode with wireless headphones kept as stereo outputs and playback on the speaker
     * rather than the receiver (feature 010, research R3, R4: `allowBluetoothA2DP` without HFP, so a headset's
     * microphone is never used); 48 kHz with a 5 ms buffer. True when the session is active.
     */
    fun activateForRecording(): Boolean {
        val options = AVAudioSessionCategoryOptionAllowBluetoothA2DP or AVAudioSessionCategoryOptionDefaultToSpeaker
        val category = withError { session.setCategory(AVAudioSessionCategoryPlayAndRecord, AVAudioSessionModeMeasurement, options, it) }
        withError { session.setPreferredSampleRate(PREFERRED_SAMPLE_RATE, it) }
        withError { session.setPreferredIOBufferDuration(PREFERRED_IO_BUFFER_SECONDS, it) }
        val active = withError { session.setActive(true, it) }
        if (active) isActive = true
        lastInputUid = currentInputUid()
        return category && active
    }

    /** True when the current route's outputs are only the built-in speaker or receiver (feature 010, FR-017). */
    fun outputIsSpeaker(): Boolean {
        val outputs = session.currentRoute.outputs.mapNotNull { (it as? AVAudioSessionPortDescription)?.portType }
        return outputs.isEmpty() || outputs.all { it in builtInOutputs }
    }

    /** The current input port's name, for the route monitor; empty when none. */
    fun currentInputName(): String = session.currentRoute.inputs.firstNotNullOfOrNull { (it as? AVAudioSessionPortDescription)?.portName } ?: ""

    /** The current output port's name, for the route monitor; empty when none. */
    fun currentOutputName(): String = session.currentRoute.outputs.firstNotNullOfOrNull { (it as? AVAudioSessionPortDescription)?.portName } ?: ""

    /** The current input port's UID, for the route monitor; empty when none. */
    fun currentInputUid(): String = session.currentRoute.inputs.firstNotNullOfOrNull { (it as? AVAudioSessionPortDescription)?.UID } ?: ""

    @Volatile
    private var lastInputUid: String = ""

    /** Deactivates and tells other apps they may resume; true on success. */
    fun deactivate(): Boolean {
        val deactivated = withError { session.setActive(false, AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation, it) }
        if (deactivated) isActive = false
        return deactivated
    }

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
     * internal so tests need no real notification. A lost output is reported unless the previous route was only the
     * built-in speaker or receiver: a new device or a lost built-in output keeps the audio going.
     */
    internal fun handleRouteChange(reason: ULong, previousOutputs: List<String>) {
        if (reason == AVAudioSessionRouteChangeReasonOldDeviceUnavailable && previousOutputs.any { it !in builtInOutputs }) {
            _events.tryEmit(AudioEvent.OutputRouteLost)
        }
        // Feature 010 (research R4, R12): the input port changing ends a take; the output changing tells the host
        // whether the speaker is now the output (monitoring must stop on it).
        val inputUid = currentInputUid()
        if (inputUid != lastInputUid) {
            lastInputUid = inputUid
            _events.tryEmit(AudioEvent.InputRouteChanged)
        }
        _events.tryEmit(AudioEvent.OutputRouteChanged(outputIsSpeaker()))
        routeChangeListener?.invoke()
    }
}
