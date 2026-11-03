package audio.tembra.android.desktop

import android.Manifest
import android.os.Build
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import audio.tembra.android.AndroidAppGraph
import audio.tembra.android.TembraApplication
import audio.tembra.engine.TickSnapshot
import audio.tembra.engine.TransportSnapshot
import audio.tembra.engine.perf.AudioHealth

/**
 * The running app as the desktop tests see it (feature 011): its graph (the instrumentation shares the app's process),
 * its controls through the perf anchors (`testTagsAsResourceId` on the root), and the engine's clock and health read the
 * way the screens read them, on the main thread.
 */
object DesktopApp {
    private const val TIMEOUT_MS = 10_000L

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    val device: UiDevice get() = UiDevice.getInstance(instrumentation)
    val graph: AndroidAppGraph get() = (instrumentation.targetContext.applicationContext as TembraApplication).appGraph

    /** Playback starts the foreground service, whose notification needs this on Android 13+ (memory: grant first). */
    fun grantNotifications() {
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(instrumentation.targetContext.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** The control tagged [anchor], waiting up to ten seconds for it. */
    fun find(anchor: String): UiObject2 =
        checkNotNull(device.wait(Until.findObject(By.res(anchor)), TIMEOUT_MS)) { "no control tagged $anchor appeared" }

    fun tap(anchor: String) {
        find(anchor).click()
        instrumentation.waitForIdleSync()
    }

    /** The control showing [text] exactly, waiting up to ten seconds for it. */
    fun findText(text: String): UiObject2 =
        checkNotNull(device.wait(Until.findObject(By.text(text)), TIMEOUT_MS)) { "nothing shows \"$text\"" }

    fun tick(): TickSnapshot {
        val snapshot = TickSnapshot()
        instrumentation.runOnMainSync { graph.audioEngine.readTick(snapshot) }
        return snapshot
    }

    fun transport(): TransportSnapshot {
        val snapshot = TransportSnapshot()
        instrumentation.runOnMainSync { graph.audioEngine.readTransport(snapshot) }
        return snapshot
    }

    fun health(): AudioHealth = checkNotNull(graph.audioEngine.audioHealth()) { "no stream is open" }

    /** Waits until [condition] holds, polling every 50 ms, for at most [timeoutMs]. */
    fun waitUntil(what: String, timeoutMs: Long = TIMEOUT_MS, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (!condition()) {
            if (SystemClock.uptimeMillis() >= deadline) {
                val shot = screenshot("timeout-${what.replace(Regex("[^A-Za-z0-9]+"), "-")}")
                error("timed out waiting for $what (screen: $shot)")
            }
            SystemClock.sleep(50)
        }
    }

    /**
     * Saves the screen as `<name>.png` in the app's external files (`adb pull
     * /storage/emulated/<user>/Android/data/com.tembraaudio/files/desktop-tests/`), for a failed wait to show what was on
     * screen.
     */
    fun screenshot(name: String): String {
        val dir = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "desktop-tests").apply { mkdirs() }
        val file = java.io.File(dir, "$name.png")
        device.takeScreenshot(file)
        return file.path
    }
}
