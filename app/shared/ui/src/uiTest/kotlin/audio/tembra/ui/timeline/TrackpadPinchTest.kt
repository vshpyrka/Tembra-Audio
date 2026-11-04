package audio.tembra.ui.timeline

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.core.project.GainDb
import audio.tembra.core.project.TrackId
import audio.tembra.ui.platform.LocalTrackpadPinchSource
import audio.tembra.ui.platform.TrackpadPinch
import audio.tembra.ui.platform.TrackpadPinchSource
import audio.tembra.ui.theme.TembraTheme
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The iPad's trackpad pinch (feature 011, spec FR-014, research R9): Compose Multiplatform 1.12 delivers none, so the
 * shell's bridge emits a [TrackpadPinch] (scale and focus in the window's pixels) that the timeline turns into a zoom
 * around the focus, in the lanes' coordinates. Driven by a fake source.
 */
@OptIn(ExperimentalTestApi::class)
class TrackpadPinchTest {

    private class FakeSource : TrackpadPinchSource {
        override val pinches = MutableSharedFlow<TrackpadPinch>(extraBufferCapacity = 4)
    }

    @Test
    fun aPinchZoomsTheViewportAroundItsFocus() = runComposeUiTest {
        val source = FakeSource()
        val zooms = mutableListOf<Pair<Double, Double>>()
        var density = 1f
        setContent {
            TembraTheme {
                density = LocalDensity.current.density
                CompositionLocalProvider(LocalTrackpadPinchSource provides source) {
                    Box(Modifier.requiredSize(1200.dp, 800.dp)) {
                        TimelineScreenContent(
                            state = TimelineUiState(
                                loaded = true, timeSignature = TimeSignature.FourFour,
                                tracks = persistentListOf(TrackUi(TrackId("t1"), "Drums", null, 0, GainDb.Unity, false, false, true, persistentListOf())),
                            ),
                            callbacks = TimelineCallbacks.None.copy(onZoomAround = { x, factor -> zooms += x to factor }),
                            telemetry = ScriptedTransportTelemetry.stopped(0.0),
                            layout = TimelineLayout.Expanded,
                            snackbarHostState = remember { SnackbarHostState() },
                        )
                    }
                }
            }
        }
        waitForIdle()
        // 500 dp from the screen's left edge (in the window's pixels): 300 dp into the lanes, past the 200 dp header column.
        val root = onNodeWithTag(TimelineTestTags.ROOT).getUnclippedBoundsInRoot()
        val top = onNodeWithTag(TimelineTestTags.TRACK_LIST).getUnclippedBoundsInRoot().top
        source.pinches.tryEmit(TrackpadPinch(scale = 2f, focus = Offset((root.left.value + 500f) * density, (top.value + 20f) * density)))
        waitForIdle()
        assertEquals(1, zooms.size, "the pinch did not zoom: $zooms")
        assertEquals(2.0, zooms.single().second)
        assertTrue(abs(zooms.single().first - 300.0 * density) <= 2.0 * density, "the zoom centre ${zooms.single().first} px is not 300 dp into the lanes")
    }
}
