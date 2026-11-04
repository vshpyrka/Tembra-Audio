package audio.tembra.android

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import audio.tembra.ui.platform.AudioRoute
import audio.tembra.ui.platform.AudioRouteMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The routes on Android (feature 010, research R4, R12): `AudioManager.getDevices` for the current input and output
 * and an `AudioDeviceCallback` on the main looper for changes. The output is the speaker when no wired, USB,
 * Bluetooth A2DP, HDMI or line output is attached; the input is the first wired input the system would route
 * capture to, the built-in microphone otherwise (Bluetooth SCO is never started, so a headset's microphone never
 * appears).
 */
class AndroidAudioRouteMonitor(context: Context) : AudioRouteMonitor {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val _route = MutableStateFlow(read())
    override val route: StateFlow<AudioRoute> = _route.asStateFlow()

    init {
        audioManager.registerAudioDeviceCallback(
            object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = refresh()
                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = refresh()
            },
            Handler(Looper.getMainLooper()),
        )
    }

    private fun refresh() {
        _route.value = read()
    }

    private fun read(): AudioRoute {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val external = outputs.firstOrNull { it.type in externalOutputTypes }
        val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        val input = inputs.firstOrNull { it.type in wiredInputTypes } ?: inputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
        return AudioRoute(
            outputIsSpeaker = external == null,
            outputName = external?.productName?.toString() ?: SPEAKER,
            inputName = input?.productName?.toString() ?: BUILT_IN_MIC,
            inputId = input?.id?.toString() ?: "",
        )
    }

    private companion object {
        const val SPEAKER = "Speaker"
        const val BUILT_IN_MIC = "Built-in microphone"

        val wiredInputTypes = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_ACCESSORY, AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_LINE_DIGITAL,
        )

        val externalOutputTypes = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_LINE_DIGITAL, AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HEARING_AID,
        )
    }
}
