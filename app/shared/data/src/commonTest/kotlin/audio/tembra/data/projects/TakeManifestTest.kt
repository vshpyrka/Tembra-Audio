package audio.tembra.data.projects

import audio.tembra.core.project.Beats
import audio.tembra.core.project.TrackId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The staged take's manifest (contracts/take-storage.md): it parses and prints itself, ignores fields a later version
 * may add, and reads as null for anything malformed so the repository can drop the take instead of crashing on open.
 */
class TakeManifestTest {

    private val manifest = TakeManifest(
        token = "3f2c1a9e",
        trackId = TrackId("track-1"),
        startBeats = Beats(4.0),
        takeName = "Take 1",
        startedAt = 1_700_000_000_000L,
        alignmentFrames = 96,
        sampleRate = 48_000,
        channels = 1,
    )

    @Test
    fun roundTripsThroughItsOwnJson() {
        val text = manifest.toJson()
        assertEquals(manifest, TakeManifest.parse(text))
        assertEquals(1, manifest.formatVersion)
        assertEquals(TakeManifest.CURRENT_VERSION, manifest.formatVersion)
        assertTrue("\"formatVersion\": 1" in text, text)
        assertTrue("\"trackId\": \"track-1\"" in text, "a track id is a bare string: $text")
        assertTrue("\"startBeats\": 4.0" in text, "beats are a bare double: $text")
        assertTrue("\"startedAt\": 1700000000000" in text, text)
        assertTrue(text.endsWith("\n"), "the file ends with a newline like the document")
    }

    @Test
    fun anUnknownFieldIsIgnored() {
        val text = manifest.toJson().replaceFirst("{", "{\n  \"future\": [1, 2],")
        assertEquals(manifest, TakeManifest.parse(text))
    }

    @Test
    fun malformedTextReadsAsNull() {
        assertNull(TakeManifest.parse(""))
        assertNull(TakeManifest.parse("{ not json"))
        assertNull(TakeManifest.parse("[]"))
        assertNull(TakeManifest.parse("{}"), "every field but the version is required")
        assertNull(TakeManifest.parse(manifest.toJson().replace("\"channels\": 1", "\"channels\": \"one\"")))
    }
}
