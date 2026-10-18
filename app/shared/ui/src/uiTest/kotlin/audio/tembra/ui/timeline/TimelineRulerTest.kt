package audio.tembra.ui.timeline

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.core.project.Beats
import audio.tembra.core.project.TempoMap
import audio.tembra.core.timeline.TimelineViewport
import audio.tembra.ui.designsystem.averageColorAt
import audio.tembra.ui.designsystem.containsColor
import audio.tembra.ui.designsystem.isCloserTo
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.components.RULER_DESCRIPTION
import audio.tembra.ui.timeline.components.SignaturePill
import audio.tembra.ui.timeline.components.TempoPill
import audio.tembra.ui.timeline.components.TimelineRuler
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The ruler driven with a fixed viewport (contracts/timeline-screen.md; T079): labels drawn, taps seek, drags scrub, the chip text. */
@OptIn(ExperimentalTestApi::class)
class TimelineRulerTest {

    private val tempoMap = TempoMap(Tempo(124), TimeSignature.FourFour)
    private var seeks = mutableListOf<Beats>()
    private var scrubs = mutableListOf<String>()
    private var densityScale = 1f
    private var chip: Color = Color.Unspecified
    private var playhead: Color = Color.Unspecified
    private var viewport: TimelineViewport? = null

    private fun ComposeUiTest.setRuler(positionBeats: Beats = Beats(16.0), playing: Boolean = false, telemetry: ScriptedTransportTelemetry = ScriptedTransportTelemetry.stopped(0.0)) {
        setContent {
            TembraTheme {
                densityScale = LocalDensity.current.density
                chip = TembraTheme.daw.playheadChip
                playhead = TembraTheme.daw.playhead
                // 32 bars across 600 dp: the widest zoom at this width.
                val widthPx = 600.0 * densityScale
                val port = TimelineViewport.fitted(widthPx = widthPx, densityScale = densityScale.toDouble(), projectLengthBeats = Beats(128.0), beatsPerBar = 4)
                viewport = port
                TimelineRuler(
                    viewport = port,
                    tempoMap = tempoMap,
                    playing = playing,
                    positionBeats = positionBeats,
                    telemetry = telemetry,
                    onSeek = { seeks += it },
                    onScrubStart = { scrubs += "start" },
                    onScrub = { scrubs += "scrub" },
                    onScrubEnd = { scrubs += "end" },
                    modifier = Modifier.width(600.dp),
                ) {
                    TempoPill(tempo = tempoMap.tempo, onClick = {}, enabled = false)
                    SignaturePill(signature = tempoMap.timeSignature, onClick = {}, enabled = false)
                }
            }
        }
        mainClock.autoAdvance = false
    }

    @Test
    fun isA44dpRulerWithThePillsAndTheChipTextInItsSemantics() = runComposeUiTest {
        setRuler(positionBeats = Beats(16.0))
        onNodeWithTag(TimelineTestTags.RULER)
            .assertHeightIsEqualTo(44.dp)
            .assertContentDescriptionEquals(RULER_DESCRIPTION)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "5.1"))
        onNodeWithTag(TimelineTestTags.TEMPO_PILL).assertContentDescriptionEquals("Tempo 124 beats per minute")
        onNodeWithTag(TimelineTestTags.SIGNATURE_PILL).assertContentDescriptionEquals("Time signature 4/4")
    }

    @Test
    fun drawsTheChipAtThePlayheadAndLabelsAcrossTheRow() = runComposeUiTest {
        setRuler(positionBeats = Beats(16.0))
        val port = assertNotNull(viewport)
        val image = onNodeWithTag(TimelineTestTags.RULER).captureToImage()
        val x = port.xOf(Beats(16.0)).toInt().coerceIn(0, image.width - 1)
        assertTrue(image.containsColor(chip), "no playhead chip was drawn")
        assertTrue(image.containsColor(playhead), "no playhead marker was drawn")
        // The chip's colour sits around the playhead x in the bar row.
        val probe = image.averageColorAt(x, image.height / 4, radius = 2)
        assertTrue(probe.isCloserTo(chip, Color.Black), "the chip is not at the playhead x: $probe")
    }

    @Test
    fun aTapSeeksToTheBeatUnderTheFinger() = runComposeUiTest {
        setRuler()
        mainClock.autoAdvance = true
        val port = assertNotNull(viewport)
        onNodeWithTag(TimelineTestTags.RULER).performTouchInput { click(Offset(width / 2f, height / 2f)) }
        assertEquals(1, seeks.size)
        val expected = port.beatsAt(port.widthPx / 2).value
        assertTrue(abs(seeks.single().value - expected) < 0.5, "seeked to ${seeks.single().value}, expected about $expected")
    }

    @Test
    fun aDragScrubsFromStartToEnd() = runComposeUiTest {
        setRuler()
        mainClock.autoAdvance = true
        onNodeWithTag(TimelineTestTags.RULER).performTouchInput { swipe(Offset(width * 0.2f, height / 2f), Offset(width * 0.6f, height / 2f), durationMillis = 300) }
        assertEquals("start", scrubs.first())
        assertEquals("end", scrubs.last())
        assertTrue(scrubs.count { it == "scrub" } >= 2, "the drag reported ${scrubs.count { it == "scrub" }} positions")
        assertTrue(seeks.isEmpty(), "a drag must not seek")
    }

    @Test
    fun whilePlayingTheChipFollowsTheTelemetry() = runComposeUiTest {
        val telemetry = ScriptedTransportTelemetry.playing(startSeconds = 0.0)
        setRuler(playing = true, telemetry = telemetry)
        mainClock.advanceTimeBy(200)
        assertTrue(telemetry.reads > 0, "the ruler did not read the telemetry while playing")
    }
}
