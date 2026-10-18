package audio.tembra.data.projects

import audio.tembra.core.project.AudioAsset
import audio.tembra.core.project.AudioAssetId
import audio.tembra.core.project.Beats
import audio.tembra.core.project.Clip
import audio.tembra.core.project.GainDb
import audio.tembra.core.project.InstrumentLabel
import audio.tembra.core.project.Project
import audio.tembra.core.project.ProjectDocument
import audio.tembra.core.project.Track
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The project document contract (contracts/project-document.md): round trips, golden text, evolution rules. */
class ProjectDocumentCodecTest {

    private val codec = ProjectDocumentCodec()
    private val demo = DemoProject.build(DemoProjectIds.fixed())

    private fun decoded(text: String): ProjectDocument = assertIs<DecodeResult.Decoded>(codec.decode(text)).document

    @Test
    fun theDemoProjectRoundTrips() {
        val document = ProjectDocument(project = demo)
        assertEquals(document, decoded(codec.encode(document)))
    }

    @Test
    fun aThirtyTwoTrackProjectRoundTrips() {
        val document = ProjectDocument(project = DataFixtures.manyTracks(32))
        assertEquals(document, decoded(codec.encode(document)))
    }

    @Test
    fun theDemoDocumentMatchesTheGoldenText() {
        assertEquals(DemoProjectGolden.TEXT, codec.encode(ProjectDocument(project = demo)))
    }

    @Test
    fun unknownKeysAreIgnoredAtEveryLevel() {
        val text = codec.encode(ProjectDocument(project = demo))
            .replace("\"formatVersion\": 1,", "\"formatVersion\": 1,\n  \"grid\": \"sixteenth\",\n  \"zoom\": 12.5,")
            .replace("\"name\": \"Demo project\",", "\"name\": \"Demo project\",\n    \"cloud\": { \"synced\": true },")
            .replace("\"muted\": false,", "\"muted\": false,\n        \"pan\": 0.0,")
            .replace("\"offsetFrames\": 0,", "\"offsetFrames\": 0,\n            \"fadeIn\": 12,")
        assertEquals(demo, decoded(text).project)
    }

    @Test
    fun aNewerDocumentIsRefusedAsTooNew() {
        val text = codec.encode(ProjectDocument(project = demo)).replace("\"formatVersion\": 1", "\"formatVersion\": 2")
        assertEquals(DocumentTooNew(2), codec.decode(text))
    }

    @Test
    fun silenceGainAndFullPrecisionBeatsRoundTrip() {
        val odd = Beats(0.1 + 0.2)
        val project = DataFixtures.project(
            tracks = listOf(DataFixtures.track(1, DataFixtures.clip(1, AudioAssetId("c".repeat(64)), start = odd.value), gainDb = GainDb.Silence)),
            assets = mapOf(AudioAssetId("c".repeat(64)) to DataFixtures.stereoAsset()),
        )
        val text = codec.encode(ProjectDocument(project = project))
        assertTrue("\"gainDb\": -Infinity" in text, text)
        val back = decoded(text).project
        assertEquals(GainDb.Silence, back.tracks[0].gainDb)
        assertEquals(odd, back.tracks[0].clips[0].startBeats)
    }

    @Test
    fun theSerialisedTextCarriesNoPath() {
        val text = codec.encode(ProjectDocument(project = demo))
        val pathLike = Regex("""(/[A-Za-z0-9_.\-]+){2,}|file:|content:|[A-Za-z]:\\""")
        assertNull(pathLike.find(text), "a path-like token appeared in the document")
    }

    @Test
    fun fieldOrderIsTheContractsWireOrder() {
        assertEquals(listOf("formatVersion", "project"), ProjectDocument.serializer().descriptor.names())
        assertEquals(
            listOf("id", "name", "description", "key", "tempo", "timeSignature", "masterVolume", "metronomeOn", "assets", "tracks"),
            Project.serializer().descriptor.names(),
        )
        assertEquals(listOf("id", "name", "label", "colorIndex", "gainDb", "muted", "soloed", "clips"), Track.serializer().descriptor.names())
        assertEquals(listOf("id", "assetId", "startBeats", "offsetFrames", "lengthFrames", "name"), Clip.serializer().descriptor.names())
        assertEquals(listOf("fileName", "format", "channels", "sampleRate", "frames", "byteSize"), AudioAsset.serializer().descriptor.names())
    }

    @Test
    fun unknownEnumValuesMapToTheDocumentedFallbacks() {
        val text = codec.encode(ProjectDocument(project = demo))
            .replace("\"label\": \"drums\"", "\"label\": \"theremin\"")
            .replace("\"key\": \"Dmin\"", "\"key\": \"Dbmin\"")
            .replace("\"fileName\": \"pad.wav\",\n        \"format\": \"wav\"", "\"fileName\": \"pad.wav\",\n        \"format\": \"opus\"")
        val project = decoded(text).project
        assertEquals(InstrumentLabel.Other, project.tracks[0].label)
        assertNull(project.key)
        val pad = project.assets.getValue(DemoProject.BUNDLED_ASSET_IDS.getValue("pad"))
        assertNull(pad.format)
        assertFalse(pad.isAvailable)
    }

    @Test
    fun malformedTextAndMissingVersionAreMalformed() {
        assertIs<DecodeResult.Malformed>(codec.decode("not json"))
        assertIs<DecodeResult.Malformed>(codec.decode("{ \"project\": {} }"))
        assertIs<DecodeResult.Malformed>(codec.decode("[]"))
    }

    @Test
    fun tempoAndSignatureOutOfRangeAreNamedValidationFailures() {
        val base = codec.encode(ProjectDocument(project = demo))
        assertEquals(DecodeResult.Invalid(listOf(ValidationFailure.TempoOutOfRange(300))), codec.decode(base.replace("\"tempo\": 124", "\"tempo\": 300")))
        assertEquals(DecodeResult.Invalid(listOf(ValidationFailure.UnknownTimeSignature("9/8"))), codec.decode(base.replace("\"timeSignature\": \"4/4\"", "\"timeSignature\": \"9/8\"")))
    }

    @Test
    fun aDocumentThatFailsValidationIsInvalidWithTheReasons() {
        val base = codec.encode(ProjectDocument(project = demo))
        val result = codec.decode(base.replace("\"lengthFrames\": 743226,\n            \"name\": \"pad\"", "\"lengthFrames\": 743227,\n            \"name\": \"pad\""))
        val invalid = assertIs<DecodeResult.Invalid>(result)
        assertEquals(listOf(ValidationFailure.ClipOutsideAsset(DemoProjectIds.fixed().clipIds[4])), invalid.failures)
    }

    private fun SerialDescriptor.names(): List<String> = (0 until elementsCount).map { getElementName(it) }
}
