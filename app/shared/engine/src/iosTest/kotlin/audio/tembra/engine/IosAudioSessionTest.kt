package audio.tembra.engine

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import platform.AVFAudio.AVAudioSessionInterruptionTypeBegan
import platform.AVFAudio.AVAudioSessionPortBuiltInSpeaker
import platform.AVFAudio.AVAudioSessionPortHeadphones
import platform.AVFAudio.AVAudioSessionRouteChangeReasonNewDeviceAvailable
import platform.AVFAudio.AVAudioSessionRouteChangeReasonOldDeviceUnavailable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The AVAudioSession wrapper the iOS engine actual uses (research R3, analysis C1; feature 004 R6). */
class IosAudioSessionTest {

    @Test
    fun listeningPlaybackAndDeactivationSucceedOnTheSimulator() {
        repeat(2) {
            assertTrue(IosAudioSession.activateForListening(), "activateForListening")
            assertTrue(IosAudioSession.deactivate(), "deactivate after listening")
            assertTrue(IosAudioSession.activateForPlayback(), "activateForPlayback")
            assertTrue(IosAudioSession.deactivate(), "deactivate after playback")
        }
    }

    @Test
    fun anActivePlaybackSessionReportsAnOutputLatency() {
        assertTrue(IosAudioSession.activateForPlayback(), "activateForPlayback")
        val latency = IosAudioSession.outputLatencyNanos()
        IosAudioSession.deactivate()
        assertTrue(latency > 0L, "output latency $latency ns")
    }

    @Test
    fun aBeganInterruptionInterrupts() = runTest {
        val collected = collectWhile { IosAudioSession.handleInterruption(AVAudioSessionInterruptionTypeBegan, 0uL) }
        assertEquals(listOf<AudioEvent>(AudioEvent.Interrupted), collected)
    }

    @Test
    fun losingHeadphonesLosesTheOutputRoute() = runTest {
        val collected = collectWhile {
            IosAudioSession.handleRouteChange(AVAudioSessionRouteChangeReasonOldDeviceUnavailable, listOfNotNull(AVAudioSessionPortHeadphones))
        }
        assertEquals(listOf<AudioEvent>(AudioEvent.OutputRouteLost), collected)
    }

    @Test
    fun losingTheSpeakerOrGainingADeviceEmitsNothing() = runTest {
        val collected = collectWhile {
            IosAudioSession.handleRouteChange(AVAudioSessionRouteChangeReasonOldDeviceUnavailable, listOfNotNull(AVAudioSessionPortBuiltInSpeaker))
            IosAudioSession.handleRouteChange(AVAudioSessionRouteChangeReasonNewDeviceAvailable, listOfNotNull(AVAudioSessionPortHeadphones))
        }
        assertEquals(emptyList(), collected)
    }

    /** Subscribes to [IosAudioSession.events], runs [block], and returns what arrived within 200 ms of virtual time. */
    private suspend fun TestScope.collectWhile(block: () -> Unit): List<AudioEvent> {
        val collected = mutableListOf<AudioEvent>()
        val collector = launch { withTimeoutOrNull(200) { IosAudioSession.events.toList(collected) } }
        testScheduler.runCurrent()
        block()
        collector.join()
        return collected
    }
}
