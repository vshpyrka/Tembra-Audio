package audio.tembra.engine

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.CoroutineContext
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Opening the transport requests focus and opens the Oboe stream, closing it closes the stream (T102): both run on the
 * engine's injected blocking dispatcher, never on the caller's thread, which is the main-immediate session scope. The
 * mixer's calls (feature 012) are the opposite case: atomic stores and a seqlock copy, synchronous by design, so they
 * never dispatch at all.
 */
class NativeAudioEngineBlockingDispatchTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val blocking = CountingDispatcher(Dispatchers.IO.limitedParallelism(1))
    private val engine = NativeAudioEngine(context, blocking = blocking)

    @AfterTest
    fun tearDown() = runBlocking {
        engine.closeTransport()
    }

    @Test
    fun openTransportAndCloseTransportHopToTheBlockingDispatcher() = runBlocking {
        val beforeOpen = blocking.dispatches
        assertEquals(TransportOpenResult.Opened, engine.openTransport())
        assertTrue(blocking.dispatches > beforeOpen, "openTransport ran on the caller's thread instead of the blocking dispatcher")
        val beforeClose = blocking.dispatches
        engine.closeTransport()
        assertTrue(blocking.dispatches > beforeClose, "closeTransport ran on the caller's thread instead of the blocking dispatcher")
        assertEquals(AudioState.Idle, engine.audioState)
    }

    /** Feature 012 (contracts/skills-and-audit.md §C part 3): the mixer's setters and read are synchronous and bounded; no hop. */
    @Test
    fun theMixerSettersAndReadDoNotDispatch() = runBlocking {
        assertEquals(TransportOpenResult.Opened, engine.openTransport())
        engine.setArrangement(ArrangementSnapshot(tracks = listOf(TrackRender(1f, true)), clips = emptyList()))
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
