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
 * main thread drops frames on every arm tap.
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
