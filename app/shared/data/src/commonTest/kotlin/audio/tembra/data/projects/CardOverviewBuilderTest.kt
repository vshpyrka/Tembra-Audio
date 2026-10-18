package audio.tembra.data.projects

import audio.tembra.core.project.AudioAssetId
import audio.tembra.core.project.GainDb
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The 48-span mix overview on the library card (spec FR-054). */
class CardOverviewBuilderTest {

    private val fileSystem = FakeFileSystem()
    private val loud = AudioAssetId("1".repeat(64))
    private val quiet = AudioAssetId("2".repeat(64))

    private fun overview(id: AudioAssetId, amplitude: Int): WaveformOverview {
        val path = "/cache/${id.sha256}.peaks".toPath()
        fileSystem.createDirectories(path.parent!!)
        fileSystem.write(path) { write(DataFixtures.peaks(2, 1_536_000) { _, _ -> amplitude }) }
        return WaveformOverviewReader.read(fileSystem, path)!!
    }

    private val overviews = mapOf(loud to overview(loud, 100), quiet to overview(quiet, 120))

    /** Track 1 (colour 0) holds the loud asset over the first half; track 2 (colour 3, −6 dB) the quiet asset over the whole 128 beats. */
    private fun project(muteFirst: Boolean = false, firstGain: GainDb = GainDb.Unity) = DataFixtures.project(
        tracks = listOf(
            DataFixtures.track(1, DataFixtures.clip(1, loud), colorIndex = 0, muted = muteFirst, gainDb = firstGain),
            DataFixtures.track(2, DataFixtures.clip(2, quiet), DataFixtures.clip(3, quiet, start = 64.0), colorIndex = 3, gainDb = GainDb(-6.0206f)),
        ),
        assets = mapOf(loud to DataFixtures.stereoAsset(), quiet to DataFixtures.stereoAsset()),
    )

    private fun CardOverview.peak(span: Int): Int = peaks[span].toInt() and 0xFF

    private fun CardOverview.color(span: Int): Int = colors[span].toInt()

    @Test
    fun fortyEightSpansWithThePeakAfterGainAndTheLoudestTracksColour() {
        val card = CardOverviewBuilder.build(project(), overviews)
        assertEquals(48, card.spans)
        assertEquals(48, card.peaks.size)
        assertEquals(48, card.colors.size)
        assertEquals(201, card.peak(0))
        assertEquals(0, card.color(0))
        assertEquals(201, card.peak(23))
        assertEquals(120, card.peak(24))
        assertEquals(3, card.color(24))
        assertEquals(120, card.peak(47))
        assertEquals(3, card.color(47))
    }

    @Test
    fun aMutedTrackDoesNotCount() {
        val card = CardOverviewBuilder.build(project(muteFirst = true), overviews)
        assertEquals(120, card.peak(0))
        assertEquals(3, card.color(0))
    }

    @Test
    fun gainAboveUnityClampsAtFullScale() {
        val card = CardOverviewBuilder.build(project(firstGain = GainDb(6f)), overviews)
        assertEquals(255, card.peak(0))
        assertEquals(0, card.color(0))
    }

    @Test
    fun aMissingOverviewCountsAsSilenceAndAnEmptyProjectIsEmpty() {
        val card = CardOverviewBuilder.build(project(), mapOf(quiet to overviews.getValue(quiet)))
        assertEquals(120, card.peak(0))
        assertEquals(3, card.color(0))
        assertEquals(CardOverview.Empty, CardOverviewBuilder.build(DataFixtures.project(), overviews))
        assertEquals(0, CardOverview.Empty.spans)
    }

    @Test
    fun theOverviewSerialisesAsBase64Columns() {
        val card = CardOverviewBuilder.build(project(), overviews)
        val text = ProjectDocumentCodec().json.encodeToString(CardOverview.serializer(), card)
        assertTrue("\"spans\": 48" in text, text)
        assertTrue(Regex("\"peaks\": \"[A-Za-z0-9+/=]+\"").containsMatchIn(text), text)
        assertTrue(Regex("\"colors\": \"[A-Za-z0-9+/=]+\"").containsMatchIn(text), text)
        assertEquals(card, ProjectDocumentCodec().json.decodeFromString(CardOverview.serializer(), text))
    }
}
