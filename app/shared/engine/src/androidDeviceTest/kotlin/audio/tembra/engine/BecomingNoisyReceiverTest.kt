package audio.tembra.engine

import android.content.Intent
import android.media.AudioManager
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Headphones unplugged while the metronome runs (feature 004 R6, spec FR-027). `ACTION_AUDIO_BECOMING_NOISY` is a
 * protected broadcast: `sendBroadcast` from the test's uid throws `SecurityException` ("not allowed to send broadcast"),
 * so the intent is delivered to the registered receiver object, and the registration itself is proven by the context
 * accepting and then refusing an `unregisterReceiver` of that object.
 */
class BecomingNoisyReceiverTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val emitted = CountDownLatch(1)
    private val receiver = BecomingNoisyReceiver(context) { emitted.countDown() }

    @AfterTest
    fun tearDown() = receiver.unregister()

    @Test
    fun theBroadcastReachesTheCallbackWithinOneSecond() {
        receiver.register()
        receiver.receiver.onReceive(context, Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        assertTrue(emitted.await(1, TimeUnit.SECONDS), "no callback within one second")
    }

    @Test
    fun otherActionsAreIgnored() {
        receiver.register()
        receiver.receiver.onReceive(context, Intent(AudioManager.ACTION_HEADSET_PLUG))
        assertFalse(emitted.await(100, TimeUnit.MILLISECONDS), "a headset plug is not a lost route")
    }

    @Test
    fun registrationReachesTheContextAndUnregisteringTwiceNeverThrows() {
        receiver.register()
        receiver.register()
        receiver.unregister()
        receiver.unregister()
        // The context throws for a receiver it does not hold: the first unregister above would have thrown had
        // register() not reached the context, and now the context no longer holds it.
        assertFailsWith<IllegalArgumentException> { context.unregisterReceiver(receiver.receiver) }
    }
}
