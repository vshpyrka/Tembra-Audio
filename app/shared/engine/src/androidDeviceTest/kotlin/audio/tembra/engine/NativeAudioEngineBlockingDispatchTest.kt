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
 * engine's injected blocking dispatcher, never on the caller's thread, which is the main-immediate session scope.
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
