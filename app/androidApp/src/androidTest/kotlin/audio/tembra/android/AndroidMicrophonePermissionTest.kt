package audio.tembra.android

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
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
import java.util.regex.Pattern

/** The Android permission implementation behind the shared interface (research R6, analysis C1). */
class AndroidMicrophonePermissionTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext

    /**
     * The runner's grant; where the platform refuses it (ColorOS on the OPPO CPH2239, Android 11, throws a
     * SecurityException unless its "Disable permission monitoring" developer option is on) the system's own prompt,
     * answered as a user would, so every test runs there too.
     */
    @Before
    fun grantTheMicrophone() {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) return
        try {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.RECORD_AUDIO)
        } catch (refused: SecurityException) {
            grantThroughTheSystemPrompt()
        }
        assertEquals(PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
    }

    private fun grantThroughTheSystemPrompt() {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = instrumentation.startActivitySync(intent)
        instrumentation.runOnMainSync { activity.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1) }
        val device = UiDevice.getInstance(instrumentation)
        val allow = By.text(Pattern.compile("While using the app|Allow", Pattern.CASE_INSENSITIVE))
        val button = device.wait(Until.findObject(allow), 10_000)
        assertNotNull("no microphone prompt appeared", button)
        button.click()
        device.wait(Until.gone(allow), 5_000)
        instrumentation.runOnMainSync { activity.finish() }
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

    /** Feature 010 (FR-019, FR-021): recording reuses the tuner's RECORD_AUDIO and the manifest declares the microphone service type. */
    @Test
    fun theRecordingPurposeReusesTheSamePermissionAndTheServiceTypeIsDeclared() {
        val permission = AndroidMicrophonePermission(context, MicrophonePermissionHost())
        assertEquals(MicrophonePermissionStatus.Granted, permission.status.value)
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val requested = info.requestedPermissions?.toList().orEmpty()
        assertTrue("RECORD_AUDIO declared", Manifest.permission.RECORD_AUDIO in requested)
        assertTrue("FOREGROUND_SERVICE_MICROPHONE declared", "android.permission.FOREGROUND_SERVICE_MICROPHONE" in requested)
    }
}
