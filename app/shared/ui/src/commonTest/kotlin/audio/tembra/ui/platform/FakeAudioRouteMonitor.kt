package audio.tembra.ui.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A scripted route for the controller and ViewModel tests: [set] plays a headphone plug, an unplug or a new input. */
class FakeAudioRouteMonitor(initial: AudioRoute = AudioRoute.BuiltIn) : AudioRouteMonitor {
    private val _route = MutableStateFlow(initial)
    override val route: StateFlow<AudioRoute> = _route.asStateFlow()

    fun set(route: AudioRoute) {
        _route.value = route
    }

    /** Wired headphones: the output is no longer the speaker; the input stays the built-in microphone. */
    fun plugHeadphones() = set(AudioRoute(outputIsSpeaker = false, outputName = "Headphones", inputName = "Built-in mic", inputId = "builtin"))

    fun unplugHeadphones() = set(AudioRoute(outputIsSpeaker = true, outputName = "Speaker", inputName = "Built-in mic", inputId = "builtin"))

    /** A wired headset with its own microphone: the input changes too. */
    fun plugHeadset() = set(AudioRoute(outputIsSpeaker = false, outputName = "Headset", inputName = "Headset mic", inputId = "headset"))
}
