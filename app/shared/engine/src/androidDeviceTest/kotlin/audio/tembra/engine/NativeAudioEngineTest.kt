package audio.tembra.engine

import androidx.test.platform.app.InstrumentationRegistry
import audio.tembra.core.version.SemanticVersion
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Links the real native engine: the only Kotlin tests that do (Constitution III). No microphone is needed. */
class NativeAudioEngineTest {
    private val engine: AudioEngine = NativeAudioEngine(InstrumentationRegistry.getInstrumentation().targetContext)

    @AfterTest
    fun tearDown() {
        engine.stopTone()
        engine.stopListening()
    }

    @Test
    fun reportsNameAndVersion() {
        val descriptor = engine.describe()
        assertEquals("Tembra Engine", descriptor.name)
        assertEquals(SemanticVersion(0, 2, 0), descriptor.version)
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

    @Test
    fun startsIdleAndReadsTheRestingState() {
        assertEquals(AudioState.Idle, engine.audioState)
        val snapshot = PitchSnapshot().apply { hasPitch = true }
        engine.readPitch(snapshot)
        assertFalse(snapshot.hasPitch)
        assertFalse(snapshot.synthetic)
        assertEquals(AudioState.Idle, snapshot.state)
        assertEquals(0L, snapshot.sequence)
    }

    @Test
    fun rejectsAnInvalidWav() {
        assertNull(engine.loadSample(byteArrayOf(0, 1, 2)))
    }

    @Test
    fun aToneStartsAndStopsWithoutAMicrophone() {
        assertEquals(AudioResult.Ok, engine.startTone(440.0, 200))
        assertEquals(AudioState.Tone, engine.audioState)
        engine.stopTone()
        assertEquals(AudioState.Idle, engine.audioState)
    }
}

private const val EXPECT_OBOE = true
