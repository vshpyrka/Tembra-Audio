package audio.tembra.ui.platform

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSBundle
import platform.Foundation.NSDictionary
import platform.Foundation.dictionaryWithContentsOfFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The iOS background host (contracts/background-playback.md): the `.playback` session does the work, the host only agrees. */
class IosBackgroundPlaybackHostTest {

    @Test
    fun keepAliveIsGrantedAndReleaseIsQuiet() {
        val host = IosBackgroundPlaybackHost()
        assertTrue(host.keepAlive())
        host.release()
    }

    @Test
    fun stopRequestsCompletesWithoutEmitting() = runTest {
        assertEquals(emptyList<Unit>(), IosBackgroundPlaybackHost().stopRequests.toList())
    }

    @Test
    fun theAppDeclaresTheAudioBackgroundMode() {
        val modes = NSBundle.mainBundle.objectForInfoDictionaryKey("UIBackgroundModes") ?: appInfoPlistValue("UIBackgroundModes")
        assertNotNull(modes, "UIBackgroundModes is missing")
        assertTrue("audio" in (modes as List<*>), "UIBackgroundModes = $modes")
    }

    /**
     * The Kotlin/Native test binary runs outside the app bundle, so its main bundle has no Info.plist; the app's
     * Info.plist is then read from the repository, six levels above the binary's directory
     * (`app/shared/ui/build/bin/iosSimulatorArm64/debugTest`).
     */
    private fun appInfoPlistValue(key: String): Any? {
        val path = NSBundle.mainBundle.bundlePath + "/../../../../../../iosApp/iosApp/Info.plist"
        return NSDictionary.dictionaryWithContentsOfFile(path)?.get(key)
    }
}
