package audio.tembra.ui.timeline

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.core.project.Beats
import audio.tembra.core.timeline.TimelineViewport
import audio.tembra.ui.designsystem.averageColorAt
import audio.tembra.ui.designsystem.distanceTo
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.components.PlayheadOverlay
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The playhead overlay reads the scripted telemetry in draw under a frozen clock (T079; research R7). */
@OptIn(ExperimentalTestApi::class)
class PlayheadOverlayTest {

    private var densityScale = 1f
    private var playhead: Color = Color.Unspecified
    private var background: Color = Color.Unspecified
    private lateinit var viewport: TimelineViewport

    private fun ComposeUiTest.setOverlay(playing: Boolean, positionBeats: Beats, telemetry: ScriptedTransportTelemetry) {
        // Paused before composing: the test clock cancels infinite animations (the frame tick) while it auto-advances.
        mainClock.autoAdvance = false
        setContent {
            TembraTheme {
                densityScale = LocalDensity.current.density
                playhead = TembraTheme.daw.playhead
                background = TembraTheme.colors.background
                val widthPx = 400.0 * densityScale
                // Ten beats across 400 dp: one beat is 40 dp.
                viewport = TimelineViewport(pixelsPerBeat = 40.0, scrollBeats = Beats.Zero, widthPx = widthPx, densityScale = densityScale.toDouble())
                var position by remember { mutableStateOf(positionBeats) }
                PlayheadOverlay(viewport = viewport, signature = TimeSignature.FourFour, playing = playing, positionBeats = position, telemetry = telemetry, modifier = Modifier.size(400.dp, 100.dp))
            }
        }
        mainClock.autoAdvance = false
    }

    /** The centre of the columns in the playhead colour, or null without one (the stroke spans several pixels at device densities). */
    private fun ComposeUiTest.playheadX(): Int? {
        val image = onRoot().captureToImage() // the overlay fills the test's box; the tagged node is the narrow accessible line
        val columns = (0 until image.width).filter { x -> image.averageColorAt(x, image.height / 2, radius = 0).distanceTo(playhead) < 0.15f }
        return if (columns.isEmpty()) null else (columns.sum().toDouble() / columns.size).roundToInt()
    }

    @Test
    fun stoppedItDrawsAtThePositionAndDescribesIt() = runComposeUiTest {
        setOverlay(playing = false, positionBeats = Beats(5.0), telemetry = ScriptedTransportTelemetry.stopped(0.0))
        onNodeWithTag(TimelineTestTags.PLAYHEAD).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "2.2"))
        val x = playheadX()
        assertTrue(x != null, "no playhead line found")
        assertTrue(abs(viewport.xOf(Beats(5.0)).toInt() - x) <= 1, "the stopped playhead is not at beat 5 (within a pixel): $x")
    }

    @Test
    fun playingItReadsTheTelemetryEveryFrameAndMoves() = runComposeUiTest {
        // Two beats per second: after one second of frames the line has moved 80 dp.
        val telemetry = ScriptedTransportTelemetry.playing(startSeconds = 0.0, beatsPerSecond = 2.0)
        setOverlay(playing = true, positionBeats = Beats.Zero, telemetry = telemetry)
        mainClock.advanceTimeByFrame()
        val first = playheadX()
        val readsBefore = telemetry.reads
        assertTrue(readsBefore > 0, "the overlay never read the telemetry")
        mainClock.advanceTimeBy(1_000)
        val later = playheadX()
        assertTrue(telemetry.reads > readsBefore, "the overlay stopped reading the telemetry")
        assertTrue(first != null && later != null, "no playhead line while playing")
        assertTrue(later > first + (40 * densityScale).toInt(), "the playhead did not move: $first → $later")
    }
}
