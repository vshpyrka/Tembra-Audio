package audio.tembra.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Audio focus around the engine's output (research R7; feature 004 R6): transient, duckable focus for reference tones
 * and the preview click, full focus for the running metronome. A permanent or transient loss is reported as
 * [AudioEvent.Interrupted] and a regain as [AudioEvent.InterruptionEnded]; a duckable loss is left to the system, which
 * lowers the volume itself because neither request pauses when ducked. Listening never requests focus.
 */
class AndroidAudioFocus(context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val _events = MutableSharedFlow<AudioEvent>(extraBufferCapacity = 16)
    val events: Flow<AudioEvent> = _events

    private val listener = AudioManager.OnAudioFocusChangeListener { change -> onFocusChange(change) }

    private val transientMayDuck = buildRequest(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
    private val gain = buildRequest(AudioManager.AUDIOFOCUS_GAIN)

    /** The granted focus [abandon] releases; null when nothing is held. Internal so device tests can observe it. */
    @Volatile
    internal var held: FocusType? = null
        private set

    private fun buildRequest(focusGain: Int): AudioFocusRequest = AudioFocusRequest.Builder(focusGain)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        .setWillPauseWhenDucked(false)
        .setAcceptsDelayedFocusGain(false)
        .setOnAudioFocusChangeListener(listener)
        .build()

    /** The focus listener's mapping; internal so device tests can inject a change without another app. */
    internal fun onFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            -> _events.tryEmit(AudioEvent.Interrupted)
            AudioManager.AUDIOFOCUS_GAIN -> _events.tryEmit(AudioEvent.InterruptionEnded)
            // AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK and any other value: the system ducks; nothing to report.
            else -> Unit
        }
    }

    private fun requestFor(type: FocusType): AudioFocusRequest = when (type) {
        FocusType.TransientMayDuck -> transientMayDuck
        FocusType.Gain -> gain
    }

    /**
     * True when focus was granted. Both requests share one listener, so Android treats them as one focus client: a
     * second request replaces the first. Callers request only when the engine leaves Idle (contracts/metronome-audio-kotlin.md).
     */
    fun request(type: FocusType = FocusType.TransientMayDuck): Boolean {
        val granted = audioManager.requestAudioFocus(requestFor(type)) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (granted) held = type
        return granted
    }

    /** Releases the held focus; does nothing when none is held. */
    fun abandon() {
        val type = held ?: return
        held = null
        audioManager.abandonAudioFocusRequest(requestFor(type))
    }
}
