package audio.tembra.ui.timeline

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.core.project.Beats
import audio.tembra.core.project.GainDb
import audio.tembra.core.project.GridSize
import audio.tembra.core.project.InstrumentLabel
import audio.tembra.core.project.PlaybackState
import audio.tembra.core.project.TrackId
import audio.tembra.core.project.ZoomControl
import audio.tembra.core.timeline.TimelineViewport
import audio.tembra.data.projects.OverviewStatus
import audio.tembra.ui.perf.PerfAnchors
import audio.tembra.ui.theme.DawColors
import audio.tembra.ui.theme.LocalDawColors
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.components.DELETE_CONFIRM_TAG
import audio.tembra.ui.timeline.components.DELETE_DIALOG_TAG
import audio.tembra.ui.timeline.components.METRONOME_SHEET_TAG
import audio.tembra.ui.timeline.components.TEMPO_DIALOG_TAG
import kotlinx.collections.immutable.persistentListOf
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Timeline content at Compact and Expanded under a frozen clock, semantics before pixels
 * (contracts/timeline-screen.md; T079, User Story 1): the demo's tracks and clips, the chrome, the first-content
 * state, and the delete confirmation; since D1 (T141) the toolbar's tempo pill, the metronome sheet's signature, the
 * snap sheet, the zoom-control choice and a ruler whose first bar nothing covers. The state is held so the callbacks
 * that open sheets or change the grid and the zoom control show their result, as the ViewModel's state does.
 */
@OptIn(ExperimentalTestApi::class)
class TimelineScreenTest {

    private val drumsId = TrackId("t1")
    private val padId = TrackId("t2")
    private val calls = mutableListOf<String>()
    private val screen = mutableStateOf(TimelineUiState())

    private val callbacks = TimelineCallbacks.None.copy(
        onPlayPause = { calls += "play" },
        onConfirmDeleteTrack = { calls += "deleteConfirmed" },
        onDismissDeleteTrack = { calls += "deleteDismissed" },
        onOpenSheet = {
            calls += "sheet($it)"
            screen.value = screen.value.copy(sheet = it)
        },
        onMetronomeSettings = {
            calls += "metronomeSettings"
            screen.value = screen.value.copy(sheet = TimelineSheet.MetronomeSettings)
        },
        onTimeSignature = { calls += "signature(${it.label})" },
        onGrid = {
            calls += "grid(${it.label})"
            screen.value = screen.value.copy(grid = it)
        },
        onZoomControl = {
            calls += "zoomControl(${it.id})"
            screen.value = screen.value.copy(zoomControl = it)
        },
        onZoomStep = { calls += "zoom($it)" },
        onFitProject = { calls += "fit" },
        onScrollTo = { calls += "scrollTo" },
    )

    private fun demoState(ready: Boolean = true): TimelineUiState {
        val status = if (ready) OverviewStatus.Ready else OverviewStatus.Building(0.2f)
        val drums = TrackUi(
            id = drumsId, name = "Drums", label = InstrumentLabel.Drums, colorIndex = 0, gainDb = GainDb.Unity, muted = false, soloed = false, audible = true,
            clips = persistentListOf(FakeOverviews.clip("c1", "drums", 0.0, 64.0, status = status, withOverview = ready)),
        )
        val pad = TrackUi(
            id = padId, name = "Pad", label = InstrumentLabel.Synth, colorIndex = 3, gainDb = GainDb(-2.4f), muted = true, soloed = false, audible = false,
            clips = persistentListOf(FakeOverviews.clip("c2", "pad", 0.0, 32.0), FakeOverviews.clip("c3", "pad", 32.0, 32.0)),
        )
        return TimelineUiState(
            loaded = true,
            projectName = "Demo project",
            tempo = Tempo(124),
            timeSignature = TimeSignature.FourFour,
            tracks = persistentListOf(drums, pad),
            lengthBeats = Beats(64.0),
            lengthSeconds = 64.0 * 60.0 / 124.0,
            viewport = TimelineViewport.fitted(widthPx = 800.0, densityScale = 1.0, projectLengthBeats = Beats(64.0), beatsPerBar = 4),
            isFirstContentReady = ready,
        )
    }

    /**
     * The screen over [state] at [layout]; [width] fixes the window's width whatever the device's (a phone's 360 dp, or
     * an expanded window's 900 dp, which may hang past a phone's edges), [daw] swaps the DAW colours.
     */
    private fun ComposeUiTest.setScreen(state: TimelineUiState, layout: TimelineLayout, width: Dp? = null, daw: DawColors? = null) {
        screen.value = state
        mainClock.autoAdvance = false
        setContent {
            TembraTheme {
                CompositionLocalProvider(LocalDawColors provides (daw ?: LocalDawColors.current)) {
                    Box(if (width != null) Modifier.requiredWidth(width).fillMaxHeight() else Modifier) {
                        TimelineScreenContent(
                            state = screen.value,
                            callbacks = callbacks,
                            telemetry = ScriptedTransportTelemetry.stopped(0.0),
                            layout = layout,
                            snackbarHostState = SnackbarHostState(),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun compactShowsTheDemoWithHeaderStripsAboveTheLanes() = runComposeUiTest {
        setScreen(demoState(), TimelineLayout.Compact)
        onNodeWithTag(TimelineTestTags.ROOT).assertIsDisplayed().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, READY))
        onNodeWithTag(TimelineTestTags.READY_MARKER).assertExists()
        onNodeWithTag(TimelineTestTags.TOOLBAR).assertExists()
        onNodeWithTag(PerfAnchors.TIMELINE_RULER).assertExists()
        // The tempo pill sits in the toolbar between redo and the overflow button (spec FR-036, FR-055, D1).
        val redo = onNodeWithTag(TimelineTestTags.REDO).getUnclippedBoundsInRoot()
        val tempo = onNodeWithTag(TimelineTestTags.TOOLBAR_TEMPO).assertContentDescriptionEquals("Tempo 124 beats per minute").getUnclippedBoundsInRoot()
        val overflow = onNodeWithTag(TimelineTestTags.OVERFLOW_BUTTON).getUnclippedBoundsInRoot()
        val toolbar = onNodeWithTag(TimelineTestTags.TOOLBAR).getUnclippedBoundsInRoot()
        assertTrue(tempo.left >= redo.right && tempo.right <= overflow.left, "the tempo pill $tempo is not between redo $redo and the overflow $overflow")
        assertTrue(tempo.top >= toolbar.top && tempo.bottom <= toolbar.bottom, "the tempo pill $tempo is not in the toolbar $toolbar")
        // The default zoom control: the buttons at the ruler's right end (spec FR-015, D1).
        onNodeWithTag(TimelineTestTags.ZOOM_LEVEL).assertContentDescriptionEquals("Zoom 1×, fit the project")
        onNodeWithTag(TimelineTestTags.OVERVIEW).assertDoesNotExist()
        onNodeWithTag(TimelineTestTags.header(0)).assertContentDescriptionEquals("Drums, Drums, +0.0dB").assertHeightIsEqualTo(56.dp)
        onNodeWithTag(TimelineTestTags.header(1)).assertContentDescriptionEquals("Pad, Synth, −2.4dB, muted")
        onNodeWithTag(TimelineTestTags.gain(0), useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag(TimelineTestTags.lane(0)).assertHeightIsEqualTo(88.dp)
        onNodeWithTag(TimelineTestTags.clip(0)).assertContentDescriptionEquals("drums, track 1, bar 1 beat 1, 31 seconds")
        onNodeWithTag(TimelineTestTags.clip(2)).assertContentDescriptionEquals("pad, track 2, bar 9 beat 1, 15 seconds")
        onNodeWithTag(TimelineTestTags.PLAYHEAD).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "1.1"))
        onNodeWithTag(PerfAnchors.TRANSPORT_READOUT).assertExists()
        onNodeWithTag(PerfAnchors.TRANSPORT_READOUT).assertContentDescriptionEquals("00:00.00 of 00:30.00")
        onNodeWithTag(PerfAnchors.TRANSPORT_PLAY).assertIsEnabled().performClick()
        assertEquals(listOf("play"), calls)
    }

    @Test
    fun expandedPinsTheHeaderColumnWithTheGainReadout() = runComposeUiTest {
        setScreen(demoState(), TimelineLayout.Expanded)
        onNodeWithTag(TimelineTestTags.header(0)).assertWidthIsEqualTo(200.dp).assertHeightIsEqualTo(96.dp)
        onNodeWithTag(TimelineTestTags.gain(0), useUnmergedTree = true).assertExists()
        onNodeWithTag(TimelineTestTags.lane(1)).assertHeightIsEqualTo(96.dp)
        onNodeWithTag(TimelineTestTags.clip(1)).assertExists()
    }

    @Test
    fun theWidestAndTheClosestZoomBothRenderTheClips() = runComposeUiTest {
        val widest = demoState()
        setScreen(widest.copy(viewport = widest.viewport.fit()), TimelineLayout.Compact)
        onNodeWithTag(TimelineTestTags.clip(0)).assertExists()
        onNodeWithTag(TimelineTestTags.clip(2)).assertExists()
    }

    @Test
    fun theClosestZoomShowsOnlyWhatIsInView() = runComposeUiTest {
        val base = demoState()
        val closest = base.viewport.withZoom(audio.tembra.core.project.ZoomLevel(base.viewport.closestPixelsPerBeat))
        setScreen(base.copy(viewport = closest), TimelineLayout.Compact)
        onNodeWithTag(TimelineTestTags.clip(0)).assertExists()
        onNodeWithTag(TimelineTestTags.clip(2)).assertDoesNotExist()
    }

    /**
     * The ruler carries bars and time only (spec FR-013, D1): on expanded windows the 200 dp over the header column stays
     * empty and the canvas starts and ends where the lanes do, with the full toolbar's tempo pill before the metronome
     * button (FR-017, FR-036).
     */
    @Test
    fun expandedKeepsTheHeaderColumnSlotEmptyAndTheCanvasAligned() = runComposeUiTest {
        setScreen(demoState(), TimelineLayout.Expanded, width = 900.dp)
        onNodeWithTag("timeline.ruler.tempo").assertDoesNotExist()
        onNodeWithTag("timeline.ruler.signature").assertDoesNotExist()
        onNodeWithTag(TimelineTestTags.header(1)).assertWidthIsEqualTo(200.dp)
        val ruler = onNodeWithTag(PerfAnchors.TIMELINE_RULER).getUnclippedBoundsInRoot()
        val lane = onNodeWithTag(TimelineTestTags.lane(0)).getUnclippedBoundsInRoot()
        val header = onNodeWithTag(TimelineTestTags.header(0)).getUnclippedBoundsInRoot()
        assertTrue(abs((ruler.left - lane.left).value) < 0.5f, "the ruler's canvas starts at ${ruler.left}, the lanes at ${lane.left}")
        assertTrue(abs((ruler.right - lane.right).value) < 0.5f, "the ruler's canvas ends at ${ruler.right}, the lanes at ${lane.right}")
        assertTrue(abs((ruler.left - header.right).value) < 0.5f, "the canvas does not start at the header column's edge ${header.right}")
        val slot = with(density) { Rect(header.left.toPx(), ruler.top.toPx(), ruler.left.toPx(), ruler.bottom.toPx()) }
        val inSlot = onAllNodes(hasClickAction()).fetchSemanticsNodes().map { it.boundsInRoot }.filter { it.overlaps(slot) }
        assertEquals(emptyList(), inSlot, "nothing sits in the ruler's header-column slot $slot")
        val tempo = onNodeWithTag(TimelineTestTags.TOOLBAR_TEMPO).getUnclippedBoundsInRoot()
        val metronome = onNodeWithTag(TimelineTestTags.METRONOME_SETTINGS).getUnclippedBoundsInRoot()
        assertTrue(tempo.right <= metronome.left, "the tempo pill $tempo is not before the metronome button $metronome")
        onNodeWithTag(TimelineTestTags.GRID).assertContentDescriptionEquals("Snap 1/16")
    }

    /** The tempo pill opens the dialog (spec FR-036); the metronome sheet's menu changes the signature (FR-037, FR-039; D1). */
    @Test
    fun theToolbarTempoPillOpensTheDialogAndTheSheetsSignatureMenuChangesTheSignature() = runComposeUiTest {
        setScreen(demoState(), TimelineLayout.Compact)
        mainClock.autoAdvance = true // the dialog, the menus and the sheet are popups that need frames to appear
        onNodeWithTag(TimelineTestTags.TOOLBAR_TEMPO).performClick()
        assertEquals(listOf("sheet(TempoDialog)"), calls)
        onNodeWithTag(TEMPO_DIALOG_TAG).assertExists()
        screen.value = screen.value.copy(sheet = null)
        waitForIdle()
        onNodeWithTag(TimelineTestTags.OVERFLOW_BUTTON).performClick()
        onNodeWithTag(TimelineTestTags.OVERFLOW_METRONOME_SETTINGS).performClick()
        onNodeWithTag(METRONOME_SHEET_TAG).assertExists()
        onNodeWithTag(TimelineTestTags.METRONOME_SHEET_SIGNATURE).performClick()
        onNodeWithTag("${TimelineTestTags.METRONOME_SHEET_SIGNATURE}.${TimeSignature.ThreeFour.id}").performClick()
        assertEquals(listOf("sheet(TempoDialog)", "metronomeSettings", "signature(3/4)"), calls)
    }

    @Test
    fun anOpenTempoDialogAndTheMetronomeSheetRender() = runComposeUiTest {
        setScreen(demoState().copy(sheet = TimelineSheet.TempoDialog), TimelineLayout.Compact)
        onNodeWithTag(TEMPO_DIALOG_TAG).assertExists()
        screen.value = screen.value.copy(sheet = TimelineSheet.MetronomeSettings)
        mainClock.autoAdvance = true
        onNodeWithTag(METRONOME_SHEET_TAG).assertExists()
        onNodeWithTag(TimelineTestTags.METRONOME_SHEET_SIGNATURE).assertContentDescriptionEquals("Time signature, 4/4")
        onNodeWithTag(TEMPO_DIALOG_TAG).assertDoesNotExist()
    }

    /**
     * On a phone the overflow's "Snap: 1/16" opens the snap sheet; choosing Bar there draws the snap lines a bar apart
     * at once (spec FR-018, FR-055, D1). The lane's tokens are swapped for opaque stand-ins so a snap column reads
     * unambiguously, and its only clip lies out of view.
     */
    @Test
    fun theOverflowSnapEntryOpensTheSheetAndTheChoiceDrawsTheLines() = runComposeUiTest {
        val dpPerBeat = 10.0
        val scale = density.density.toDouble()
        val track = TrackUi(
            id = drumsId, name = "Drums", label = InstrumentLabel.Drums, colorIndex = 0, gainDb = GainDb.Unity, muted = false, soloed = false, audible = true,
            clips = persistentListOf(FakeOverviews.clip("c1", "drums", 120.0, 8.0)),
        )
        val state = demoState().copy(
            tracks = persistentListOf(track), lengthBeats = Beats(128.0),
            viewport = TimelineViewport(pixelsPerBeat = dpPerBeat, scrollBeats = Beats.Zero, widthPx = 360.0 * scale, densityScale = scale, projectLengthBeats = Beats(128.0)),
        )
        setScreen(state, TimelineLayout.Compact, width = 360.dp, daw = gridColors)
        mainClock.autoAdvance = true
        val fine = snapLineColumns()
        assertTrue(fine.size > 30, "1/16 at 10 dp a beat draws the eighths, 5 dp apart: $fine")
        onNodeWithTag(TimelineTestTags.OVERFLOW_BUTTON).performClick()
        onNodeWithTag(TimelineTestTags.OVERFLOW_SNAP).assertTextEquals("Snap: 1/16").performClick()
        onNodeWithTag(TimelineTestTags.SNAP_SHEET).assertExists()
        onNodeWithTag(TimelineTestTags.snapSheetCheck(GridSize.Sixteenth), useUnmergedTree = true).assertExists()
        onNodeWithTag(TimelineTestTags.snapSheetRow(GridSize.Bar)).performClick()
        waitForIdle()
        onNodeWithTag(TimelineTestTags.SNAP_SHEET).assertDoesNotExist()
        assertEquals(listOf("sheet(GridMenu)", "grid(Bar)", "sheet(null)"), calls)
        val bars = snapLineColumns()
        assertTrue(bars.size in 8..10, "Bar at 10 dp a beat draws a line every 40 dp across 360 dp: $bars")
        bars.zipWithNext { a, b -> b - a }.forEach { gap -> assertTrue(abs(gap - 40 * density.density) <= 1.5f, "bar lines $gap px apart in $bars") }
        onNodeWithTag(TimelineTestTags.OVERFLOW_BUTTON).performClick()
        onNodeWithTag(TimelineTestTags.OVERFLOW_SNAP).assertTextEquals("Snap: Bar")
    }

    /**
     * The overflow's "Zoom control" choice swaps the ruler's buttons for the strip above the transport bar and back,
     * the current one checked, and each control's intents reach the callbacks (spec FR-015, D1).
     */
    @Test
    fun theZoomControlChoiceSwapsTheButtonsForTheStrip() = runComposeUiTest {
        val base = demoState()
        setScreen(base.copy(viewport = base.viewport.zoomAround(0.0, 4.0)), TimelineLayout.Compact)
        mainClock.autoAdvance = true
        onNodeWithTag(TimelineTestTags.ZOOM_IN).performClick()
        onNodeWithTag(TimelineTestTags.ZOOM_OUT).performClick()
        onNodeWithTag(TimelineTestTags.ZOOM_LEVEL).performClick()
        assertEquals(listOf("zoom(1)", "zoom(-1)", "fit"), calls)
        val ruler = onNodeWithTag(PerfAnchors.TIMELINE_RULER).getUnclippedBoundsInRoot()
        val zoomIn = onNodeWithTag(TimelineTestTags.ZOOM_IN).getUnclippedBoundsInRoot()
        assertTrue(abs((zoomIn.right - ruler.right).value) < 0.5f && zoomIn.top >= ruler.top && zoomIn.bottom <= ruler.bottom, "the buttons $zoomIn are not at the ruler's right end $ruler")

        onNodeWithTag(TimelineTestTags.OVERFLOW_BUTTON).performClick()
        onNodeWithTag(TimelineTestTags.overflowZoomControl(ZoomControl.Buttons)).assertIsSelected()
        onNodeWithTag(TimelineTestTags.overflowZoomControl(ZoomControl.OverviewStrip)).assertIsNotSelected().performClick()
        onNodeWithTag(TimelineTestTags.ZOOM_IN).assertDoesNotExist()
        val strip = onNodeWithTag(TimelineTestTags.OVERVIEW).assertHeightIsEqualTo(24.dp).getUnclippedBoundsInRoot()
        val tracks = onNodeWithTag(TimelineTestTags.TRACK_LIST).getUnclippedBoundsInRoot()
        val bar = onNodeWithTag(PerfAnchors.TRANSPORT_PLAY).getUnclippedBoundsInRoot()
        assertTrue(strip.top >= tracks.bottom - 0.5.dp, "the strip $strip overlaps the lanes $tracks")
        assertTrue(strip.bottom <= bar.top, "the strip $strip is not above the transport bar $bar")
        // A tap outside the window centres it there: a scroll through the strip's callback.
        onNodeWithTag(TimelineTestTags.OVERVIEW).performTouchInput { click(Offset(width * 0.9f, height / 2f)) }
        assertTrue("scrollTo" in calls, "the strip's tap reached no scroll: $calls")

        onNodeWithTag(TimelineTestTags.OVERFLOW_BUTTON).performClick()
        onNodeWithTag(TimelineTestTags.overflowZoomControl(ZoomControl.OverviewStrip)).assertIsSelected()
        onNodeWithTag(TimelineTestTags.overflowZoomControl(ZoomControl.Buttons)).performClick()
        onNodeWithTag(TimelineTestTags.OVERVIEW).assertDoesNotExist()
        onNodeWithTag(TimelineTestTags.ZOOM_IN).assertExists()
        assertEquals(listOf("zoomControl(overviewStrip)", "zoomControl(buttons)"), calls.filter { it.startsWith("zoomControl") })
    }

    /**
     * The owner's D1 finding: on a 360 dp phone nothing covers the ruler's first bar. With a bar 100 dp wide the
     * canvas starts at the window's edge and no control lies over its first 100 dp (spec FR-013, FR-055).
     */
    @Test
    fun theRulerFirstBarIsNeverCoveredAt360dp() = runComposeUiTest {
        val scale = density.density.toDouble()
        val state = demoState().copy(
            viewport = TimelineViewport(pixelsPerBeat = 25.0, scrollBeats = Beats.Zero, widthPx = 360.0 * scale, densityScale = scale, projectLengthBeats = Beats(64.0)),
        )
        setScreen(state, TimelineLayout.Compact, width = 360.dp)
        val root = onNodeWithTag(TimelineTestTags.ROOT).getUnclippedBoundsInRoot()
        val ruler = onNodeWithTag(PerfAnchors.TIMELINE_RULER).getUnclippedBoundsInRoot()
        assertTrue(abs((ruler.left - root.left).value) < 0.5f, "the ruler's canvas starts at ${ruler.left}, not at the window's edge ${root.left}")
        val firstBar = with(density) { Rect(ruler.left.toPx(), ruler.top.toPx(), (ruler.left + 100.dp).toPx(), ruler.bottom.toPx()) }
        val covering = onAllNodes(hasClickAction()).fetchSemanticsNodes().map { it.boundsInRoot }.filter { it.overlaps(firstBar) }
        assertEquals(emptyList(), covering, "controls over the ruler's first bar $firstBar")
        onNodeWithTag(TimelineTestTags.TOOLBAR_TEMPO).assertIsDisplayed()
        onNodeWithTag(TimelineTestTags.ZOOM_OUT).assertIsDisplayed()
    }

    @Test
    fun firstContentWaitsForTheOverviews() = runComposeUiTest {
        setScreen(demoState(ready = false), TimelineLayout.Compact)
        onNodeWithTag(TimelineTestTags.ROOT).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, LOADING))
        onNodeWithTag(TimelineTestTags.READY_MARKER).assertDoesNotExist()
    }

    @Test
    fun anEmptyProjectShowsTheInvitationAndDisablesNothingOnTheTransport() = runComposeUiTest {
        setScreen(TimelineUiState(loaded = true, isFirstContentReady = true), TimelineLayout.Compact)
        onNodeWithTag(TimelineTestTags.EMPTY).assertExists()
        onNodeWithTag(TimelineTestTags.lane(0)).assertDoesNotExist()
        onNodeWithTag(PerfAnchors.TRANSPORT_PLAY).assertExists()
        onNodeWithTag(PerfAnchors.TRANSPORT_SKIP_START).assertIsEnabled()
    }

    @Test
    fun noProjectDisablesTheTransportAndTheToolbar() = runComposeUiTest {
        setScreen(TimelineUiState(), TimelineLayout.Compact)
        onNodeWithTag(TimelineTestTags.ROOT).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, LOADING))
        onNodeWithTag(PerfAnchors.TRANSPORT_SKIP_START).assertIsNotEnabled()
        onNodeWithTag(PerfAnchors.TIMELINE_ADD_TRACK).assertIsNotEnabled()
        onNodeWithTag(TimelineTestTags.TOOLBAR_TEMPO).assertIsNotEnabled()
    }

    @Test
    fun aPendingDeleteShowsTheDialogNamingTheTrack() = runComposeUiTest {
        setScreen(demoState().copy(confirmDelete = padId), TimelineLayout.Compact)
        onNodeWithTag(DELETE_DIALOG_TAG).assertExists()
        onNodeWithText("\"Pad\" and its clips are removed. Undo restores it.").assertExists()
        onNodeWithTag(DELETE_CONFIRM_TAG).performClick()
        assertEquals(listOf("deleteConfirmed"), calls)
    }

    @Test
    fun playingStateShowsPauseAndTheAudiblePosition() = runComposeUiTest {
        setScreen(demoState().copy(playback = PlaybackState.Playing, positionBeats = Beats(8.0), positionSeconds = 8.0 * 60.0 / 124.0), TimelineLayout.Compact)
        onNodeWithTag(PerfAnchors.TRANSPORT_PLAY).assertContentDescriptionEquals("Pause")
        onNodeWithTag(TimelineTestTags.PLAYHEAD).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "3.1"))
    }

    /** Opaque stand-ins for the lane's tokens, so a drawn snap column reads unambiguously as [snapColor]. */
    private val snapColor = Color(0xFF00FF00)
    private val gridColors = DawColors.Default.copy(canvasLevel0 = Color.Black, gridOverlayFaint = Color(0xFF0000FF), gridOverlay = snapColor)

    /** The first x of every run of [snapColor] pixels across the first lane's middle row. */
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
}
