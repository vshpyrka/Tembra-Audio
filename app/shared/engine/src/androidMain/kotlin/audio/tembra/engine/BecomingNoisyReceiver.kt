package audio.tembra.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build

/**
 * Reports `ACTION_AUDIO_BECOMING_NOISY` (headphones unplugged, a Bluetooth sink gone) through [onBecomingNoisy]
 * (feature 004 R6). Registered only while the metronome runs; [register] and [unregister] are idempotent. The
 * receiver is not exported (API 33+ flag; below that a dynamic receiver for a protected system broadcast needs none),
 * which is what `ContextCompat.registerReceiver(..., RECEIVER_NOT_EXPORTED)` does without adding androidx.core.
 */
class BecomingNoisyReceiver(context: Context, private val onBecomingNoisy: () -> Unit) {
    private val context: Context = context.applicationContext ?: context

    @Volatile
    private var registered = false

    /** Internal so device tests can deliver the intent: only system uids may send this protected broadcast. */
    internal val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) onBecomingNoisy()
        }
    }

    @Synchronized
    fun register() {
        if (registered) return
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        registered = true
    }

    @Synchronized
    fun unregister() {
        if (!registered) return
        context.unregisterReceiver(receiver)
        registered = false
    }
}
