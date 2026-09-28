package audio.tembra.engine

import audio.tembra.core.version.SemanticVersion
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Links the real native engine: the only Kotlin tests that do (Constitution III). No microphone is needed. */
class NativeAudioEngineTest {
    private val engine: AudioEngine = NativeAudioEngine()

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

    /**
     * The simulator's bare test process has no output audio unit (RemoteIO fails to initialise with -10851), so the
     * tone path reports the device error there and is verified in the simulator app (quickstart §5); a device plays it.
     */
    @Test
    fun aToneStartsAndStopsWithoutAMicrophone() {
        when (val result = engine.startTone(440.0, 200)) {
            AudioResult.Ok -> {
                assertEquals(AudioState.Tone, engine.audioState)
                engine.stopTone()
                assertEquals(AudioState.Idle, engine.audioState)
            }
            is AudioResult.Error -> {
                assertEquals(AudioResult.Error(-2), result, "only the device error is acceptable")
                assertEquals(AudioState.Idle, engine.audioState)
                println("NativeAudioEngineTest: no output unit in this simulator process; tone round trip skipped")
            }
        }
    }
}

private const val EXPECT_OBOE = false
