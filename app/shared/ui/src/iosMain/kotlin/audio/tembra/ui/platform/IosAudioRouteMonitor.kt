package audio.tembra.ui.platform

import audio.tembra.engine.AudioEvent
import audio.tembra.engine.IosAudioSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The routes on iOS (feature 010, research R4, R12): the session's current route, refreshed on every route-change
 * event the engine's session forwards. The output is the speaker when the route's outputs are only the built-in
 * speaker or receiver. The collector runs on the main dispatcher from the first subscription.
 */
class IosAudioRouteMonitor : AudioRouteMonitor {
    private val _route = MutableStateFlow(read())
    override val route: StateFlow<AudioRoute> = _route.asStateFlow()

    init {
        CoroutineScope(Dispatchers.Main).launch {
            IosAudioSession.events.collect { event ->
                if (event is AudioEvent.OutputRouteChanged || event is AudioEvent.InputRouteChanged || event is AudioEvent.OutputRouteLost) _route.value = read()
            }
        }
    }

    private fun read(): AudioRoute = AudioRoute(
        outputIsSpeaker = IosAudioSession.outputIsSpeaker(),
        outputName = IosAudioSession.currentOutputName(),
        inputName = IosAudioSession.currentInputName(),
        inputId = IosAudioSession.currentInputUid(),
    )
}
