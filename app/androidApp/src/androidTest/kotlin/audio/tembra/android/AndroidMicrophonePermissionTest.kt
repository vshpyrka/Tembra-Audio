package audio.tembra.android

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import androidx.test.platform.app.InstrumentationRegistry
import audio.tembra.ui.platform.MicrophonePermissionStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The Android permission implementation behind the shared interface (research R6, analysis C1). */
class AndroidMicrophonePermissionTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext

    @Before
    fun grantTheMicrophone() {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.RECORD_AUDIO)
    }

    @Test
    fun refreshReportsGrantedUnderTheRunnersGrant() {
        val permission = AndroidMicrophonePermission(context, MicrophonePermissionHost())
        permission.refresh()
        assertEquals(MicrophonePermissionStatus.Granted, permission.status.value)
    }

    @Test
    fun openSystemSettingsStartsTheAppDetailsPage() {
        var started: Intent? = null
        val recording = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) {
                started = intent
            }
        }
        AndroidMicrophonePermission(recording, MicrophonePermissionHost()).openSystemSettings()
        val intent = started
        assertNotNull(intent)
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent!!.action)
        assertEquals("package:${context.packageName}", intent.data.toString())
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun requestSuspendsUntilTheLauncherResultArrives() = runBlocking {
        var launched: String? = null
        val host = MicrophonePermissionHost { launched = it }
        val permission = AndroidMicrophonePermission(context, host)
        val pending = async { permission.request() }
        withTimeout(2_000) {
            while (launched == null) yield()
        }
        assertEquals(Manifest.permission.RECORD_AUDIO, launched)
        assertTrue(pending.isActive)
        host.onResult(true)
        assertEquals(MicrophonePermissionStatus.Granted, withTimeout(2_000) { pending.await() })
        assertEquals(MicrophonePermissionStatus.Granted, permission.status.value)
    }

    @Test
    fun aSecondRequestWhileOneIsPendingSharesItsResult() = runBlocking {
        var launches = 0
        val host = MicrophonePermissionHost { launches++ }
        val permission = AndroidMicrophonePermission(context, host)
        val first = async { permission.request() }
        withTimeout(2_000) {
            while (launches == 0) yield()
        }
        val second = async { permission.request() }
        yield()
        assertTrue(first.isActive)
        assertTrue(second.isActive)
        host.onResult(true)
        assertEquals(MicrophonePermissionStatus.Granted, withTimeout(2_000) { first.await() })
        assertEquals(MicrophonePermissionStatus.Granted, withTimeout(2_000) { second.await() })
        assertEquals("the prompt was launched again for the pending request", 1, launches)
    }
}
