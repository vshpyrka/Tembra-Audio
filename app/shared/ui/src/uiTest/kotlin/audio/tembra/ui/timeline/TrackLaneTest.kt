package audio.tembra.ui.timeline

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.core.project.Beats
import audio.tembra.core.project.GainDb
import audio.tembra.core.project.InstrumentLabel
import audio.tembra.core.project.TrackId
import audio.tembra.core.timeline.TimelineViewport
import audio.tembra.data.projects.OverviewStatus
import audio.tembra.ui.theme.DawColors
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.components.LOADING_WAVEFORM
import audio.tembra.ui.timeline.components.TrackLane
import audio.tembra.ui.timeline.components.UNAVAILABLE
import kotlinx.collections.immutable.persistentListOf
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** One lane: clip semantics and taps first, the drawn columns second (contracts/timeline-screen.md; T079, US1). */
@OptIn(ExperimentalTestApi::class)
class TrackLaneTest {

    private val trackId = TrackId("t1")
    private val drums = FakeOverviews.clip("c1", "drums", startBeats = 0.0, lengthBeats = 4.0)
    private val pad = FakeOverviews.clip("c2", "pad", startBeats = 12.0, lengthBeats = 4.0)
    private val viewport = TimelineViewport(pixelsPerBeat = 20.0, scrollBeats = Beats.Zero, widthPx = 320.0, densityScale = 1.0, projectLengthBeats = Beats(16.0))
    private val calls = mutableListOf<String>()
    private var densityScale = 1f

    private fun track(vararg clips: ClipUi) = TrackUi(
        id = trackId, name = "Drums", label = InstrumentLabel.Drums, colorIndex = 0, gainDb = GainDb.Unity,
        muted = false, soloed = false, audible = true, clips = persistentListOf(*clips),
    )

    private fun ComposeUiTest.setLane(track: TrackUi, selection: Selection = Selection.None) {
        setContent {
            TembraTheme {
                densityScale = androidx.compose.ui.platform.LocalDensity.current.density
                TrackLane(
                    track = track, index = 0, firstClipIndex = 0, viewport = viewport, selection = selection,
                    signature = TimeSignature.FourFour, height = 88.dp,
                    onSelectClip = { calls += "select(${it.value})" }, onClearSelection = { calls += "clear" },
                )
            }
        }
    }

    @Test
    fun clipsCarryTheirDescriptionAndSelectOnTap() = runComposeUiTest {
        setLane(track(drums, pad), selection = Selection.ClipSelected(trackId, pad.id))
        onNodeWithTag(TimelineTestTags.lane(0)).assertExists()
        onNodeWithTag(TimelineTestTags.clip(0)).assertContentDescriptionEquals("drums, track 1, bar 1 beat 1, 2 seconds").assertHasClickAction().assertIsNotSelected()
        onNodeWithTag(TimelineTestTags.clip(1)).assertContentDescriptionEquals("pad, track 1, bar 4 beat 1, 2 seconds").assertIsSelected()
        onNodeWithTag(TimelineTestTags.clip(0)).performClick()
        assertEquals(listOf("select(c1)"), calls)
    }

    @Test
    fun aTapOnEmptyLaneClearsTheSelection() = runComposeUiTest {
        setLane(track(drums))
        // The lane node's centre is at 8 beats, past the four-beat clip.
        onNodeWithTag(TimelineTestTags.lane(0)).performClick()
        assertEquals(listOf("clear"), calls)
    }

    @Test
    fun anUnavailableClipSaysSo() = runComposeUiTest {
        setLane(track(FakeOverviews.clip("c3", "lost", 0.0, 4.0, available = false, status = OverviewStatus.Failed)))
        onNodeWithTag(TimelineTestTags.clip(0)).assertContentDescriptionEquals("lost, track 1, bar 1 beat 1, 2 seconds, $UNAVAILABLE")
    }

    @Test
    fun aBuildingOverviewReportsItsProgress() = runComposeUiTest {
        setLane(track(FakeOverviews.clip("c4", "late", 0.0, 4.0, status = OverviewStatus.Building(0.5f), withOverview = false)))
        onNodeWithTag(TimelineTestTags.clip(0)).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "$LOADING_WAVEFORM 50 %"))
    }

    @Test
    fun waveformColumnsAreDrawnInTheTintOnceTheOverviewIsReady() = runComposeUiTest {
        setLane(track(drums))
        val tint = DawColors.Default.trackTints[0]
        assertTrue(laneHasPixelNear(tint), "a column in the track tint $tint inside the clip body")
    }

    @Test
    fun aBuildingClipDrawsNoColumns() = runComposeUiTest {
        setLane(track(FakeOverviews.clip("c5", "late", 0.0, 4.0, status = OverviewStatus.Building(0f), withOverview = false)))
        assertFalse(laneHasPixelNear(DawColors.Default.trackTints[0]))
    }

    /** Scans the first clip's body (x 10..70 dp of 80, below the caption band) for a pixel within 0.04 of [color]. */
    private fun ComposeUiTest.laneHasPixelNear(color: Color): Boolean {
        val image = onNodeWithTag(TimelineTestTags.lane(0)).captureToImage()
        val pixels = image.toPixelMap()
        val density = densityScale
        val left = (10 * density).toInt()
        val right = (70 * density).toInt().coerceAtMost(pixels.width - 1)
        val top = (30 * density).toInt()
        val bottom = (80 * density).toInt().coerceAtMost(pixels.height - 1)
        for (y in top..bottom) for (x in left..right) {
            val pixel = pixels[x, y]
            if (abs(pixel.red - color.red) < 0.04f && abs(pixel.green - color.green) < 0.04f && abs(pixel.blue - color.blue) < 0.04f) return true
        }
        return false
    }
}
