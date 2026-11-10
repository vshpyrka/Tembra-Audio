package audio.tembra.engine

import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Arming and disarming restart the RemoteIO unit and switch the audio session, hundreds of milliseconds of blocking
 * work (the iPhone 14 trial round of 2026-11-02 measured the arm at 604 ms). The session scope that calls them is
 * main-immediate, so the actual must hop to its blocking dispatcher for both, the way the Android actual does, or the
 * main thread drops frames on every arm tap. The mixer's calls (feature 012) are the opposite case: atomic stores and
 * a seqlock copy, synchronous by design, so they never dispatch at all.
 */
@OptIn(ExperimentalForeignApi::class)
class NativeAudioEngineBlockingDispatchTest {
    private val blocking = CountingDispatcher(Dispatchers.IO.limitedParallelism(1))
    private val engine = NativeAudioEngine(engineLatencySink = {}, blocking = blocking)

    @AfterTest
    fun tearDown() = runBlocking {
        engine.disarm()
        engine.closeTransport()
    }

    @Test
    fun armAndDisarmHopToTheBlockingDispatcherInsteadOfTheCallersThread() = runBlocking {
        // The bare simulator test process has no output unit (feature 003): there the arm is refused with NotTransport,
        // which must still happen on the blocking dispatcher, so the hop is observable in every environment.
        val opened = engine.openTransport()
        if (opened == TransportOpenResult.Opened) {
            engine.setTempo(Tempo(120), TimeSignature.FourFour)
            engine.setArrangement(ArrangementSnapshot(tracks = listOf(TrackRender(1f, true)), clips = emptyList()))
        } else {
            println("NativeAudioEngineBlockingDispatchTest: no output unit in this simulator process ($opened); the refused path is checked")
        }
        val beforeArm = blocking.dispatches
        val armed = engine.arm(2)
        if (opened == TransportOpenResult.Opened) assertTrue(armed is ArmResult.Armed || armed == ArmResult.InputUnavailable, "$armed")
        else assertEquals(ArmResult.NotTransport, armed)
        assertTrue(blocking.dispatches > beforeArm, "arm ran on the caller's thread instead of the blocking dispatcher")
        val beforeDisarm = blocking.dispatches
        engine.disarm()
        assertTrue(blocking.dispatches > beforeDisarm, "disarm ran on the caller's thread instead of the blocking dispatcher")
    }

    /**
     * Opening the transport activates the session and creates, initialises and starts the RemoteIO unit; closing it tears
     * the unit down and deactivates the session (T102). Both must hop like the arm: the session scope that calls them
     * is main-immediate. In the bare simulator process the open is refused with DeviceFailed, still on the dispatcher.
     */
    @Test
    fun openTransportAndCloseTransportHopToTheBlockingDispatcher() = runBlocking {
        val beforeOpen = blocking.dispatches
        val opened = engine.openTransport()
        assertTrue(blocking.dispatches > beforeOpen, "openTransport ran on the caller's thread instead of the blocking dispatcher ($opened)")
        val beforeClose = blocking.dispatches
        engine.closeTransport()
        assertTrue(blocking.dispatches > beforeClose, "closeTransport ran on the caller's thread instead of the blocking dispatcher")
    }

    /** Feature 012 (contracts/skills-and-audit.md §C part 3): the mixer's setters and read are synchronous and bounded; no hop, open or refused. */
    @Test
    fun theMixerSettersAndReadDoNotDispatch() = runBlocking {
        val opened = engine.openTransport()
        if (opened == TransportOpenResult.Opened) {
            engine.setArrangement(ArrangementSnapshot(tracks = listOf(TrackRender(1f, true)), clips = emptyList()))
        }
        val before = blocking.dispatches
        engine.setPan(0, -0.5f)
        engine.setTrack(0, 0.8f, true)
        engine.setMaster(0.9f)
        engine.setMetering(true)
        engine.readMix(MixSnapshot())
        engine.resetMixClip(0)
        engine.resetMixClip(MixSnapshot.MASTER_INDEX)
        engine.setMetering(false)
        assertEquals(before, blocking.dispatches, "a mixer call hopped to the blocking dispatcher")
    }
}

/** Counts the blocks handed to [delegate]; a `withContext` onto it from another dispatcher counts at least once. */
private class CountingDispatcher(private val delegate: CoroutineDispatcher) : CoroutineDispatcher() {
    @Volatile
    var dispatches: Int = 0
        private set

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        dispatches += 1
        delegate.dispatch(context, block)
    }
}
