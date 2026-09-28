package audio.tembra.ui.tuner

import audio.tembra.core.tuner.Instrument
import audio.tembra.core.tuner.Note
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Drop-in recordings take precedence over the synthesized tone, resolved once per path (contracts/reference-sounds.md). */
class ReferenceSoundRepositoryTest {

    private class CountingReader(private val files: Map<String, ByteArray>) : ResourceReader {
        val reads = mutableMapOf<String, Int>()
        override suspend fun readOrNull(path: String): ByteArray? {
            reads[path] = (reads[path] ?: 0) + 1
            return files[path]
        }
    }

    private val e2Bytes = byteArrayOf(1, 2, 3, 4)
    private val reader = CountingReader(mapOf("files/tuner/guitar-E2.wav" to e2Bytes))
    private val repository = ReferenceSoundRepository(reader)

    @Test
    fun aBundledRecordingWins() = runTest {
        val sound = repository.resolve(Instrument.Guitar, Note.parse("E2"))
        assertIs<ReferenceSound.Recording>(sound)
        assertEquals("guitar-E2", sound.resourceName)
        assertSame(e2Bytes, sound.bytes)
    }

    @Test
    fun aMissingRecordingFallsBackToTheSynthesizedTone() = runTest {
        val sound = repository.resolve(Instrument.Guitar, Note.parse("F#3"))
        assertIs<ReferenceSound.Synth>(sound)
        assertTrue(abs(sound.frequencyHz - 185.0) < 0.01, "F#3 is ${sound.frequencyHz} Hz")
        assertEquals(1, reader.reads["files/tuner/guitar-Fsharp3.wav"])
    }

    @Test
    fun hitsAndMissesAreCached() = runTest {
        repeat(3) { repository.resolve(Instrument.Guitar, Note.parse("E2")) }
        repeat(3) { repository.resolve(Instrument.Guitar, Note.parse("F#3")) }
        repeat(2) { repository.resolve(Instrument.Cello, Note.parse("C2")) }
        assertEquals(1, reader.reads["files/tuner/guitar-E2.wav"])
        assertEquals(1, reader.reads["files/tuner/guitar-Fsharp3.wav"])
        assertEquals(1, reader.reads["files/tuner/cello-C2.wav"])
    }
}
