package audio.tembra.data.projects

import audio.tembra.core.project.AudioAsset
import audio.tembra.core.project.AudioAssetId
import audio.tembra.core.project.AudioFormat
import audio.tembra.core.project.Beats
import audio.tembra.core.project.Clip
import audio.tembra.core.project.ClipId
import audio.tembra.core.project.ClipTemplate
import audio.tembra.core.project.CountIn
import audio.tembra.core.project.EditResult
import audio.tembra.core.project.GainDb
import audio.tembra.core.project.InstrumentLabel
import audio.tembra.core.project.Project
import audio.tembra.core.project.ProjectDocument
import audio.tembra.core.project.ProjectEditor
import audio.tembra.core.project.RecordTake
import audio.tembra.core.project.Track
import audio.tembra.core.project.TrackId
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
    private val pathLike = Regex("""(/[A-Za-z0-9_.\-]+){2,}|file:|content:|[A-Za-z]:\\""")

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
        assertNull(pathLike.find(text), "a path-like token appeared in the document")
    }

    @Test
    fun fieldOrderIsTheContractsWireOrder() {
        assertEquals(listOf("formatVersion", "project"), ProjectDocument.serializer().descriptor.names())
        assertEquals(
            listOf("id", "name", "description", "key", "tempo", "timeSignature", "masterVolume", "metronomeOn", "assets", "tracks", "countIn"),
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
            .replace("\"countIn\": \"ONE_BAR\"", "\"countIn\": \"THREE_BARS\"")
        val project = decoded(text).project
        assertEquals(InstrumentLabel.Other, project.tracks[0].label)
        assertNull(project.key)
        assertEquals(CountIn.ONE_BAR, project.countIn)
        val pad = project.assets.getValue(DemoProject.BUNDLED_ASSET_IDS.getValue("pad"))
        assertNull(pad.format)
        assertFalse(pad.isAvailable)
    }

    @Test
    fun aFeatureNineDocumentWithoutCountInReadsAsOneBar() {
        val text = codec.encode(ProjectDocument(project = demo)).replace(",\n    \"countIn\": \"ONE_BAR\"", "")
        assertFalse("countIn" in text, text)
        assertEquals(demo, decoded(text).project)
        assertEquals(CountIn.ONE_BAR, decoded(text).project.countIn)
    }

    @Test
    fun aProjectWithARecordedTakeRoundTripsByContentIdWithoutAPath() {
        val loopId = AudioAssetId("c".repeat(64))
        val takeId = DataFixtures.assetId(byteArrayOf(1, 2, 3))
        val takeAsset = AudioAsset(fileName = "Take 1.wav", format = AudioFormat.Wav, channels = 1, sampleRate = 48000, frames = 96_000, byteSize = 288_044)
        val base = DataFixtures.project(tracks = listOf(DataFixtures.track(1, DataFixtures.clip(1, loopId))), assets = mapOf(loopId to DataFixtures.stereoAsset()))
        val edit = RecordTake(
            trackId = TrackId("track-1"),
            assetId = takeId,
            asset = takeAsset,
            clipId = ClipId("take-1"),
            clip = ClipTemplate(assetId = takeId, name = "Take 1", offsetFrames = 0, lengthFrames = 96_000),
            startBeats = Beats(16.0),
        )
        val recorded = assertIs<EditResult.Applied>(ProjectEditor.apply(base, edit)).project
        val document = ProjectDocument(project = recorded.copy(countIn = CountIn.TWO_BARS))
        val text = codec.encode(document)
        assertTrue("\"${takeId.sha256}\": {" in text, text)
        assertTrue("\"countIn\": \"TWO_BARS\"" in text, text)
        assertNull(pathLike.find(text), "a path-like token appeared in the document")
        val back = decoded(text)
        assertEquals(document, back)
        assertEquals(listOf(ClipId("clip-1"), ClipId("take-1"), ClipId("take-1-after-clip-1")), back.project.tracks[0].clips.map { it.id })
        assertEquals("Take 1", back.project.tracks[0].clips[1].name)
        assertEquals(takeAsset, back.project.assets[takeId])
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
