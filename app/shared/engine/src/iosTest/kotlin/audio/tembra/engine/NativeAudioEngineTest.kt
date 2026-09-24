package audio.tembra.engine

import audio.tembra.core.version.SemanticVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Links the real native engine: the only Kotlin test that does (Constitution III). */
class NativeAudioEngineTest {
    private val engine: AudioEngine = NativeAudioEngine()

    @Test
    fun reportsNameAndVersion() {
        val descriptor = engine.describe()
        assertEquals("Tembra Engine", descriptor.name)
        assertEquals(SemanticVersion(0, 1, 0), descriptor.version)
    }

    @Test
    fun listsTheCompiledLibraries() {
        val libraries = engine.describe().compiledLibraries
        for (prefix in listOf("miniaudio ", "dr_wav ", "signalsmith-stretch ")) {
            assertTrue(libraries.any { it.startsWith(prefix) }, "missing $prefix in $libraries")
        }
        assertEquals(EXPECT_OBOE, libraries.any { it.startsWith("oboe ") }, "oboe presence in $libraries")
    }

    @Test
    fun isStableAcrossCalls() {
        assertEquals(engine.describe(), engine.describe())
    }
}

private const val EXPECT_OBOE = false
