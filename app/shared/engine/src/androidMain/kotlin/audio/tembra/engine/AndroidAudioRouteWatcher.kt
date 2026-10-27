package audio.tembra.engine

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

/**
 * Reports the platform's device changes while the transport is open (feature 010, research R4, R12): an input device
 * appearing or disappearing is [AudioEvent.InputRouteChanged] (a running take ends), an output change is
 * [AudioEvent.OutputRouteChanged] with whether the built-in speaker is now the output (no wired, USB or Bluetooth
 * A2DP output present). Registered on the main looper; never on an audio thread.
 */
internal class AndroidAudioRouteWatcher(context: Context, private val emit: (AudioEvent) -> Unit) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var registered = false
    private var inputIds: Set<Int> = emptySet()
    private var outputIsSpeaker: Boolean = true

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = refresh()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = refresh()
    }

    /** True when no headphones, USB device or Bluetooth A2DP output is attached. */
    val isSpeaker: Boolean get() = outputIsSpeaker

    fun register() {
        if (registered) return
        registered = true
        inputIds = currentInputIds()
        outputIsSpeaker = speakerIsOutput()
        audioManager.registerAudioDeviceCallback(callback, handler)
    }

    fun unregister() {
        if (!registered) return
        registered = false
        audioManager.unregisterAudioDeviceCallback(callback)
    }

    private fun refresh() {
        val inputs = currentInputIds()
        if (inputs != inputIds) {
            inputIds = inputs
            emit(AudioEvent.InputRouteChanged)
        }
        val speaker = speakerIsOutput()
        if (speaker != outputIsSpeaker) {
            outputIsSpeaker = speaker
            emit(AudioEvent.OutputRouteChanged(speaker))
        }
    }

    private fun currentInputIds(): Set<Int> =
        audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).filter { it.type in inputTypes }.map { it.id }.toSet()

    private fun speakerIsOutput(): Boolean =
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).none { it.type in externalOutputTypes }

    private companion object {
        /** Inputs the system routes capture to (research R4): the built-in microphone, wired and USB inputs; never Bluetooth SCO. */
        val inputTypes = setOf(
            AudioDeviceInfo.TYPE_BUILTIN_MIC, AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY, AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_LINE_DIGITAL,
        )

        val externalOutputTypes = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_LINE_DIGITAL, AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HEARING_AID,
        )
    }
}
