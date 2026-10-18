package audio.tembra.ui.session

import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.core.project.AudioAsset
import audio.tembra.core.project.AudioAssetId
import audio.tembra.core.project.AudioFormat
import audio.tembra.core.project.Beats
import audio.tembra.core.project.Clip
import audio.tembra.core.project.ClipId
import audio.tembra.core.project.GainDb
import audio.tembra.core.project.Project
import audio.tembra.core.project.ProjectId
import audio.tembra.core.project.Track
import audio.tembra.core.project.TrackId
import audio.tembra.engine.ClipSourceHandle
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The project → arrangement mapping (contracts/project-session.md; T039): audibility, gain, clip order, bounds. */
class ArrangementMapperTest {

    private val assetA = AudioAssetId("a".repeat(64))
    private val assetB = AudioAssetId("b".repeat(64))
    private val asset = AudioAsset("x.wav", AudioFormat.Wav, channels = 1, sampleRate = 44100, frames = 44100, byteSize = 88244)

    private fun clip(id: String, asset: AudioAssetId, start: Double) = Clip(ClipId(id), asset, Beats(start), 0, 44100, "x")

    private fun project(vararg tracks: Track) = Project(
        id = ProjectId("p"),
        name = "P",
        tempo = Tempo(120),
        timeSignature = TimeSignature.FourFour,
        assets = persistentMapOf(assetA to asset, assetB to asset),
        tracks = tracks.toList().toPersistentList(),
    )

    private fun track(id: String, muted: Boolean = false, soloed: Boolean = false, gain: GainDb = GainDb.Unity, vararg clips: Clip) =
        Track(TrackId(id), "T$id", colorIndex = 0, gainDb = gain, muted = muted, soloed = soloed, clips = persistentListOf(*clips))

    private val sources = mapOf(assetA to ClipSourceHandle(1), assetB to ClipSourceHandle(2))

    @Test
    fun theSoloMuteTruthTableBecomesAudibility() {
        val noSolo = ArrangementMapper.map(project(track("1"), track("2", muted = true)), sources) as ArrangementResult.Mapped
        assertEquals(listOf(true, false), noSolo.snapshot.tracks.map { it.audible })
        val oneSolo = ArrangementMapper.map(project(track("1"), track("2", soloed = true), track("3")), sources) as ArrangementResult.Mapped
        assertEquals(listOf(false, true, false), oneSolo.snapshot.tracks.map { it.audible })
        val mutedAndSoloed = ArrangementMapper.map(project(track("1", muted = true, soloed = true), track("2")), sources) as ArrangementResult.Mapped
        assertEquals(listOf(false, false), mutedAndSoloed.snapshot.tracks.map { it.audible })
    }

    @Test
    fun gainIsLinearAndClipsAreOrderedPerTrackWithTheSourceRate() {
        val mapped = ArrangementMapper.map(
            project(
                track("1", gain = GainDb(-6f), clips = arrayOf(clip("c2", assetB, 8.0), clip("c1", assetA, 0.0))),
                track("2", gain = GainDb.Silence, clips = arrayOf(clip("c3", assetA, 4.0))),
            ),
            sources,
        ) as ArrangementResult.Mapped
        val snapshot = mapped.snapshot
        assertEquals(0.5012f, snapshot.tracks[0].gainLinear, absoluteTolerance = 0.001f)
        assertEquals(0f, snapshot.tracks[1].gainLinear)
        assertEquals(listOf(0.0, 8.0, 4.0), snapshot.clips.map { it.startBeats })
        assertEquals(listOf(0, 0, 1), snapshot.clips.map { it.trackIndex })
        assertEquals(listOf(1, 2, 1), snapshot.clips.map { it.source.id })
        assertTrue(snapshot.clips.all { it.sourceRate == 44100 && it.lengthFrames == 44100L })
    }

    @Test
    fun aClipWithoutAnOpenSourceIsLeftOut() {
        val mapped = ArrangementMapper.map(project(track("1", clips = arrayOf(clip("c1", assetA, 0.0), clip("c2", assetB, 4.0)))), mapOf(assetA to ClipSourceHandle(7))) as ArrangementResult.Mapped
        assertEquals(1, mapped.snapshot.clips.size)
        assertEquals(7, mapped.snapshot.clips.single().source.id)
    }

    @Test
    fun sixtyFiveTracksAreReportedNotCrashed() {
        val tracks = (1..65).map { track("$it") }.toTypedArray()
        assertIs<ArrangementResult.TooLarge>(ArrangementMapper.map(project(*tracks), sources))
    }

    @Test
    fun theFastPathStripMatchesTheSnapshot() {
        val p = project(track("1", gain = GainDb(-6f)), track("2", soloed = true))
        assertEquals(false, ArrangementMapper.track(p, 0).audible)
        assertEquals(0.5012f, ArrangementMapper.track(p, 0).gainLinear, absoluteTolerance = 0.001f)
        assertEquals(true, ArrangementMapper.track(p, 1).audible)
    }
}
