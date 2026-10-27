package audio.tembra.ui.platform

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The host double behaves as contracts/background-playback.md describes, so ViewModel tests can rely on it. */
@OptIn(ExperimentalCoroutinesApi::class)
class FakeBackgroundPlaybackHostTest {

    @Test
    fun keepAliveReturnsAllowAndTogglesAlive() {
        val host = FakeBackgroundPlaybackHost()
        assertFalse(host.alive)
        assertTrue(host.keepAlive(PlaybackNotification("Metronome", "120 BPM")))
        assertTrue(host.alive)
        host.release()
        assertFalse(host.alive)
        assertEquals(1, host.keepAliveCount)
        assertEquals(1, host.releaseCount)
    }

    @Test
    fun aRefusingHostStaysDown() {
        val host = FakeBackgroundPlaybackHost(allow = false)
        assertFalse(host.keepAlive(PlaybackNotification("Metronome", "120 BPM")))
        assertFalse(host.alive)
        assertEquals(1, host.keepAliveCount)
    }

    @Test
    fun requestStopIsCollectedFromStopRequests() = runTest {
        val host = FakeBackgroundPlaybackHost()
        var received = 0
        backgroundScope.launch { host.stopRequests.collect { received++ } }
        runCurrent()
        host.requestStop()
        runCurrent()
        host.requestStop()
        runCurrent()
        assertEquals(2, received)
    }
}
