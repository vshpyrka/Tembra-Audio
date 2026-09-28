package audio.tembra.engine

import kotlin.test.Test
import kotlin.test.assertTrue

/** The AVAudioSession wrapper the iOS engine actual uses (research R3, analysis C1). */
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
}
