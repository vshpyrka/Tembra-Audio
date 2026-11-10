package audio.tembra.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The StrictMode guard is installed in debug builds and absent in release builds (feature 012, skills-and-audit.md §C part 4). */
class StrictModePolicyTest {

    @Test
    fun aDebugBuildInstallsThePolicyOnce() {
        var installs = 0
        assertTrue(StrictModePolicy.install(debug = true) { installs++ })
        assertEquals(1, installs)
    }

    @Test
    fun aReleaseBuildInstallsNothing() {
        var installs = 0
        assertFalse(StrictModePolicy.install(debug = false) { installs++ })
        assertEquals(0, installs)
    }
}
