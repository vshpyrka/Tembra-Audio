package audio.tembra.android

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ServiceTestRule
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** The media-playback foreground service behind [AndroidBackgroundPlaybackHost] (contracts/background-playback.md). */
class AndroidBackgroundPlaybackHostTest {

    @get:Rule
    val serviceRule = ServiceTestRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val host = AndroidBackgroundPlaybackHost(context)
    private val serviceIntent = Intent(context, MetronomeForegroundService::class.java)

    @After
    fun stopTheService() {
        host.release()
    }

    @Test
    fun keepAliveRunsTheServiceInTheForegroundAsMediaPlayback() {
        assertTrue(host.keepAlive())
        val service = (serviceRule.bindService(serviceIntent) as MetronomeServiceBinder).service
        // Service.foregroundServiceType exists from API 29; below it the start itself is the evidence.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            assertTrue(
                "not in the foreground as media playback within 2 s",
                waitUntil { service.foregroundServiceType == ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK },
            )
        }
    }

    @Test
    fun releaseStopsTheServiceWithinTwoSeconds() {
        assertTrue(host.keepAlive())
        serviceRule.bindService(serviceIntent)
        serviceRule.unbindService()
        host.release()
        assertTrue("the service still runs 2 s after release()", waitUntil { !isRunning() })
    }

    /**
     * The ViewModel releases on the main thread right after `keepAlive()` when the engine start fails or the user taps
     * Pause at once; the stop must not reach the system before the service has entered the foreground, or the process
     * dies with "startForegroundService() did not then call startForeground()".
     */
    @Test
    fun releaseRightAfterKeepAliveLeavesTheProcessRunningAndStopsTheService() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertTrue(host.keepAlive())
            host.release()
        }
        assertTrue("the service still runs 2 s after release()", waitUntil { !isRunning() })
        // The system's crash message follows the stop at once; the process is still here to assert after a further second.
        SystemClock.sleep(1_000)
        assertTrue(!isRunning())
    }

    /** A service the system stops while the process lives must not leave the host believing it is still foreground. */
    @Test
    fun keepAliveStartsAgainAfterTheServiceWasStoppedBehindTheHostsBack() {
        assertTrue(host.keepAlive())
        val first = (serviceRule.bindService(serviceIntent) as MetronomeServiceBinder).service
        assertTrue("not in the foreground within 2 s", waitUntil { isForeground(first) })
        serviceRule.unbindService()
        context.stopService(serviceIntent)
        assertTrue("the service still runs 2 s after stopService", waitUntil { !isRunning() })

        assertTrue(host.keepAlive())

        val second = (serviceRule.bindService(serviceIntent) as MetronomeServiceBinder).service
        assertTrue("the second keepAlive() did not start a foreground service within 2 s", waitUntil { isForeground(second) })
    }

    /** In the foreground as media playback on API 29+; below it, running is all that can be read. */
    private fun isForeground(service: MetronomeForegroundService): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            service.foregroundServiceType == ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK

    @Test
    fun theStopActionEmitsOneStopRequest() = runBlocking {
        assertTrue(host.keepAlive())
        val collected = async { host.stopRequests.take(1).toList() }
        withTimeout(2_000) {
            while (AndroidBackgroundPlaybackHost.stopSignals.subscriptionCount.value == 0) yield()
        }
        context.startService(Intent(serviceIntent).setAction(MetronomeForegroundService.ACTION_STOP))
        assertEquals(listOf(Unit), withTimeout(2_000) { collected.await() })
        assertTrue("the Stop action left the service running", waitUntil { !isRunning() })
    }

    /** Polls [condition] for up to two seconds. */
    private fun waitUntil(condition: () -> Boolean): Boolean {
        val deadline = SystemClock.uptimeMillis() + 2_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return true
            SystemClock.sleep(50)
        }
        return condition()
    }

    /**
     * A binding without `BIND_AUTO_CREATE` connects only to a service that already exists, so no connection within
     * 200 ms means the service has been destroyed.
     */
    private fun isRunning(): Boolean {
        val connected = CountDownLatch(1)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) = connected.countDown()
            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }
        context.bindService(serviceIntent, connection, 0)
        val running = connected.await(200, TimeUnit.MILLISECONDS)
        context.unbindService(connection)
        return running
    }
}
