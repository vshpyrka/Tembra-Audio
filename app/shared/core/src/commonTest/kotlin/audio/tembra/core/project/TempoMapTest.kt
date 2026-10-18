package audio.tembra.core.project

import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Beats to seconds and frames through the one-entry tempo map (research R3; data-model.md section 1). */
class TempoMapTest {

    private fun assertClose(expected: Double, actual: Double, tolerance: Double = 1e-9) {
        assertTrue(abs(expected - actual) <= tolerance, "expected $expected, was $actual")
    }

    @Test
    fun beatsToSecondsAtFortyOneTwentyFourAndTwoForty() {
        assertClose(1.5, TempoMap(Tempo(40), TimeSignature.FourFour).beatsToSeconds(Beats(1.0)))
        assertClose(60.0 / 124.0, TempoMap(Tempo(124), TimeSignature.FourFour).beatsToSeconds(Beats(1.0)))
        assertClose(0.25, TempoMap(Tempo(240), TimeSignature.FourFour).beatsToSeconds(Beats(1.0)))
        assertClose(4.0, TempoMap(Tempo(120), TimeSignature.SixEight).secondsToBeats(2.0).value)
    }

    @Test
    fun theBeatIsTheSignatureUnitSoSixEightAndSevenEightCountEighths() {
        val sixEight = TempoMap(Tempo(120), TimeSignature.SixEight)
        assertClose(3.0, sixEight.beatsToSeconds(Beats(6.0)))
        assertEquals(6, sixEight.beatsPerBar)
        val sevenEight = TempoMap(Tempo(120), TimeSignature.SevenEight)
        assertEquals(7, sevenEight.beatsPerBar)
        assertClose(3.5, sevenEight.beatsToSeconds(sevenEight.barStart(2)))
    }

    @Test
    fun framesAtTheAssetRateRoundTrip() {
        val map = TempoMap(Tempo(124), TimeSignature.FourFour)
        assertEquals(23_226L, map.beatsToFrames(Beats(1.0), 48000))
        assertClose(1.0, map.framesToBeats(23_226, 48000).value, 1e-4)
        assertEquals(44_100L, TempoMap(Tempo(120), TimeSignature.FourFour).beatsToFrames(Beats(2.0), 44100))
        assertClose(64.0, map.framesToBeats(1_486_452, 48000).value, 1e-3)
    }

    @Test
    fun barAndBeatInBarAreOneBased() {
        val map = TempoMap(Tempo(120), TimeSignature.FourFour)
        assertEquals(1, map.barOf(Beats(0.0)))
        assertEquals(1, map.beatInBar(Beats(0.0)))
        assertEquals(1, map.barOf(Beats(3.99)))
        assertEquals(4, map.beatInBar(Beats(3.99)))
        assertEquals(5, map.barOf(Beats(16.0)))
        assertEquals(1, map.beatInBar(Beats(16.0)))
        assertEquals(Beats(16.0), map.barStart(5))
        val sevenEight = TempoMap(Tempo(120), TimeSignature.SevenEight)
        assertEquals(2, sevenEight.barOf(Beats(7.0)))
        assertEquals(3, sevenEight.beatInBar(Beats(9.5)))
    }

    @Test
    fun projectLengthIsTheEndOfTheLastClip() {
        assertEquals(Beats.Zero, ProjectFixtures.empty.lengthBeats)
        assertEquals(0.0, ProjectFixtures.empty.lengthSeconds)
        val one = ProjectFixtures.project(ProjectFixtures.track(1, ProjectFixtures.clip(1, 4.0)))
        assertClose(68.0, one.lengthBeats.value)
        assertClose(34.0, one.lengthSeconds)
        val several = ProjectFixtures.project(
            ProjectFixtures.track(1, ProjectFixtures.clip(1, 0.0)),
            ProjectFixtures.track(2, ProjectFixtures.clip(2, 100.0, assetId = ProjectFixtures.monoAssetId, length = 441_000)),
        )
        assertClose(120.0, several.lengthBeats.value)
        assertClose(60.0, several.lengthSeconds)
    }

    @Test
    fun clipLengthInBeatsFollowsTheAssetRateAndTheTempo() {
        val project = ProjectFixtures.project(ProjectFixtures.track(1, ProjectFixtures.clip(1, 0.0, assetId = ProjectFixtures.monoAssetId, length = 441_000)))
        assertClose(20.0, project.clipLengthBeats(project.tracks[0].clips[0]).value)
        assertClose(20.0, project.clipEndBeats(project.tracks[0].clips[0]).value)
        val faster = project.copy(tempo = Tempo(240))
        assertClose(40.0, faster.clipLengthBeats(faster.tracks[0].clips[0]).value)
    }

    @Test
    fun audibleTracksFollowTheSoloMuteTable() {
        val a = ProjectFixtures.track(1)
        val b = ProjectFixtures.track(2, muted = true)
        val c = ProjectFixtures.track(3, soloed = true)
        val d = ProjectFixtures.track(4, muted = true, soloed = true)
        assertEquals(setOf(a.id), ProjectFixtures.project(a, b).audibleTracks)
        assertEquals(setOf(c.id), ProjectFixtures.project(a, b, c).audibleTracks)
        assertEquals(setOf(c.id), ProjectFixtures.project(a, b, c, d).audibleTracks)
        val e = ProjectFixtures.track(5, soloed = true)
        assertEquals(setOf(c.id, e.id), ProjectFixtures.project(a, b, c, d, e).audibleTracks)
        assertEquals(emptySet(), ProjectFixtures.project(b, d).audibleTracks)
    }

    @Test
    fun unusedAssetsAreThoseNoClipReferences() {
        val project = ProjectFixtures.project(ProjectFixtures.track(1, ProjectFixtures.clip(1, 0.0)))
        assertEquals(setOf(ProjectFixtures.monoAssetId), project.unusedAssets)
        assertEquals(emptySet(), ProjectFixtures.empty.unusedAssets)
    }

    @Test
    fun lookupsByIdentity() {
        val clip = ProjectFixtures.clip(1, 0.0)
        val project = ProjectFixtures.project(ProjectFixtures.track(1, clip), ProjectFixtures.track(2))
        assertEquals(ProjectFixtures.trackId(2), project.findTrack(ProjectFixtures.trackId(2))?.id)
        assertEquals(clip, project.findClip(ProjectFixtures.clipId(1)))
        assertEquals(ProjectFixtures.trackId(1), project.trackOf(ProjectFixtures.clipId(1))?.id)
        assertEquals(null, project.findClip(ClipId("nope")))
    }
}
