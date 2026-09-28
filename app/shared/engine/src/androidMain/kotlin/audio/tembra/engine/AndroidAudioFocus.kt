package audio.tembra.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Transient, duckable audio focus for reference tones (research R7): other apps lower their volume while a tone
 * sounds and get it back on [abandon]. Focus loss is reported as [AudioEvent.Interrupted], a regain as
 * [AudioEvent.InterruptionEnded]. Listening never requests focus.
 */
class AndroidAudioFocus(context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val _events = MutableSharedFlow<AudioEvent>(extraBufferCapacity = 16)
    val events: Flow<AudioEvent> = _events

    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
            -> _events.tryEmit(AudioEvent.Interrupted)
            AudioManager.AUDIOFOCUS_GAIN -> _events.tryEmit(AudioEvent.InterruptionEnded)
        }
    }

    private val request: AudioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        .setOnAudioFocusChangeListener(listener)
        .build()

    /** True when focus was granted. */
    fun request(): Boolean = audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    fun abandon() {
        audioManager.abandonAudioFocusRequest(request)
    }
}
