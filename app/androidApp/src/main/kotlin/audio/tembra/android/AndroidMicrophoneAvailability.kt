package audio.tembra.android

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager

/**
 * The device's own answer (research R2): the microphone feature, and at least one audio input that is a microphone
 * (built in, wired, USB or Bluetooth), not a telephony, tuner or submix source. Reports the inputs connected now, so a
 * USB microphone plugged in later is seen on the next resume.
 */
class AndroidMicrophoneAvailability(private val context: Context) : MicrophoneAvailability {
    override fun hasInput(): Boolean {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)) return false
        val audio = context.getSystemService(AudioManager::class.java) ?: return false
        return audio.getDevices(AudioManager.GET_DEVICES_INPUTS).any { it.type !in NOT_MICROPHONES }
    }

    private companion object {
        /** Inputs that carry no microphone signal. */
        val NOT_MICROPHONES = setOf(
            AudioDeviceInfo.TYPE_TELEPHONY,
            AudioDeviceInfo.TYPE_FM_TUNER,
            AudioDeviceInfo.TYPE_TV_TUNER,
            AudioDeviceInfo.TYPE_REMOTE_SUBMIX,
        )
    }
}
