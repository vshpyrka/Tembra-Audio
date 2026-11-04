package audio.tembra.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
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
import audio.tembra.core.project.GridSize
import audio.tembra.core.project.InstrumentLabel
import audio.tembra.core.project.TrackId
import audio.tembra.core.timeline.TimelineViewport
import audio.tembra.data.projects.OverviewStatus
import audio.tembra.ui.theme.DawColors
import audio.tembra.ui.theme.LocalDawColors
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

/** One lane: clip semantics and taps first, the drawn columns second, the snap lines last (contracts/timeline-screen.md; T079, US1; T139, FR-018). */
@OptIn(ExperimentalTestApi::class)
class TrackLaneTest {

    private val trackId = TrackId("t1")
    private val drums = FakeOverviews.clip("c1", "drums", startBeats = 0.0, lengthBeats = 4.0)
    private val pad = FakeOverviews.clip("c2", "pad", startBeats = 12.0, lengthBeats = 4.0)
    private val viewport = TimelineViewport(pixelsPerBeat = 20.0, scrollBeats = Beats.Zero, widthPx = 320.0, densityScale = 1.0, projectLengthBeats = Beats(16.0))
    private val calls = mutableListOf<String>()
    private var densityScale = 1f

    // The snap-line tests drive these two.
    private var grid by mutableStateOf(GridSize.Bar)
    private var dpPerBeat by mutableDoubleStateOf(10.0)

    /** Opaque stand-ins for the lane's tokens, so a drawn snap column reads unambiguously as [snapColor]. */
    private val snapColor = Color(0xFF00FF00)
    private val gridColors = DawColors.Default.copy(canvasLevel0 = Color.Black, gridOverlayFaint = Color(0xFF0000FF), gridOverlay = snapColor)

    private fun track(vararg clips: ClipUi) = TrackUi(
        id = trackId, name = "Drums", label = InstrumentLabel.Drums, colorIndex = 0, gainDb = GainDb.Unity,
        muted = false, soloed = false, audible = true, clips = persistentListOf(*clips),
    )

    private fun ComposeUiTest.setLane(track: TrackUi, selection: Selection = Selection.None) {
        setContent {
            TembraTheme {
                densityScale = LocalDensity.current.density
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

    @Test
    fun drawsTheSnapLinesAtThePickedGridAndNoneWhenOff() = runComposeUiTest {
        // 10 dp a beat across 323 dp: bars 40 dp apart and quarters 10 dp apart, both past the 4 dp floor; the last line
        // of either sits at 320 dp, clear of the edge.
        setGridLane()
        val bars = snapLineColumns()
        assertEquals(9, bars.size, "one snap line a bar at Bar, at $bars")
        bars.forEachIndexed { bar, x -> assertTrue(abs(x - bar * 40 * densityScale) <= 1f, "bar ${bar + 1} drawn at $x px") }
        grid = GridSize.Quarter
        waitForIdle()
        assertEquals(33, snapLineColumns().size, "one snap line a beat at 1/4")
        grid = GridSize.Off
        waitForIdle()
        assertEquals(emptyList(), snapLineColumns(), "Off draws no snap lines")
    }

    @Test
    fun aDenseGridFallsBackToTheNextCoarserLevelThatKeepsItsLinesFourDpApart() = runComposeUiTest {
        grid = GridSize.Sixteenth
        setGridLane()
        // At 10 dp a beat a sixteenth is 2.5 dp: the eighths, 5 dp apart, are drawn instead.
        val eighths = snapLineColumns()
        assertEquals(65, eighths.size, "the eighths at 1/16 and 10 dp a beat")
        assertGapsAtLeastFourDp(eighths)
        // At 2 dp a beat even a quarter is 2 dp: the bars, 8 dp apart, are drawn.
        dpPerBeat = 2.0
        waitForIdle()
        val bars = snapLineColumns()
        assertEquals(41, bars.size, "the bars at 1/16 and 2 dp a beat")
        assertGapsAtLeastFourDp(bars)
    }

    @Test
    fun aClipScrolledPastTheLeftEdgeDrawsNothingOverWhatSitsBesideTheLane() = runComposeUiTest {
        // The expanded layout pins the headers beside the lanes (feature 011 large screens): scrolled two beats in, the
        // four-beat clip starts 20 dp left of the lane, where only the header may draw.
        val beside = Color(0xFF00FFFF)
        setContent {
            TembraTheme {
                val density = LocalDensity.current.density
                val port = TimelineViewport(
                    pixelsPerBeat = 10.0, scrollBeats = Beats(2.0), widthPx = 240.0 * density,
                    densityScale = density.toDouble(), projectLengthBeats = Beats(16.0),
                )
                Row {
                    Box(Modifier.width(80.dp).height(88.dp).background(beside).testTag(BESIDE_TAG))
                    TrackLane(
                        track = track(drums), index = 0, firstClipIndex = 0, viewport = port, selection = Selection.None,
                        signature = TimeSignature.FourFour, height = 88.dp,
                        onSelectClip = {}, onClearSelection = {}, modifier = Modifier.width(240.dp),
                    )
                }
            }
        }
        val pixels = onNodeWithTag(BESIDE_TAG).captureToImage().toPixelMap()
        val painted = (0 until pixels.width).filter { x ->
            (0 until pixels.height).any { y ->
                val pixel = pixels[x, y]
                abs(pixel.red - beside.red) > 0.04f || abs(pixel.green - beside.green) > 0.04f || abs(pixel.blue - beside.blue) > 0.04f
            }
        }
        assertEquals(emptyList(), painted, "columns of the header painted over by the lane, of ${pixels.width}")
    }

    /** An empty 323 dp lane at [grid] and [dpPerBeat] from beat 0, its viewport in the device's own pixels. */
    private fun ComposeUiTest.setGridLane() {
        setContent {
            TembraTheme {
                CompositionLocalProvider(LocalDawColors provides gridColors) {
                    val density = LocalDensity.current.density
                    densityScale = density
                    val port = TimelineViewport(
                        pixelsPerBeat = dpPerBeat, scrollBeats = Beats.Zero, widthPx = GRID_LANE_DP * density,
                        densityScale = density.toDouble(), projectLengthBeats = Beats(16.0),
                    )
                    TrackLane(
                        track = track(), index = 0, firstClipIndex = 0, viewport = port, selection = Selection.None,
                        signature = TimeSignature.FourFour, height = 88.dp, grid = grid,
                        onSelectClip = {}, onClearSelection = {}, modifier = Modifier.width(GRID_LANE_DP.dp),
                    )
                }
            }
        }
    }

    /** The first x of every run of [snapColor] pixels across the lane's middle row. */
    private fun ComposeUiTest.snapLineColumns(): List<Int> {
        val pixels = onNodeWithTag(TimelineTestTags.lane(0)).captureToImage().toPixelMap()
        val y = pixels.height / 2
        val starts = mutableListOf<Int>()
        var inRun = false
        for (x in 0 until pixels.width) {
            val pixel = pixels[x, y]
            val snap = abs(pixel.red - snapColor.red) < 0.1f && abs(pixel.green - snapColor.green) < 0.1f && abs(pixel.blue - snapColor.blue) < 0.1f
            if (snap && !inRun) starts += x
            inRun = snap
        }
        return starts
    }

    private fun assertGapsAtLeastFourDp(columns: List<Int>) {
        val gaps = columns.zipWithNext { a, b -> b - a }
        assertTrue(gaps.all { it >= 4 * densityScale - 1 }, "snap lines closer than 4 dp: $gaps px at density $densityScale")
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

private const val GRID_LANE_DP = 323.0
private const val BESIDE_TAG = "lane.beside"
