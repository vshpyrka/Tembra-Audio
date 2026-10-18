package audio.tembra.ui.timeline.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import audio.tembra.ui.metronome.components.MenuEntry
import audio.tembra.ui.metronome.components.MenuPill
import audio.tembra.ui.timeline.TimelineCopy
import audio.tembra.ui.timeline.TimelineTestTags
import kotlin.math.roundToInt

/**
 * The zoom readout (spec FR-015, FR-016): "1×" relative to the widest level, a pill whose menu offers "Fit project" and
 * the zoom steps; tagged `timeline.toolbar.zoom`.
 */
@Composable
internal fun ZoomReadout(zoomFactor: Double, onFitProject: () -> Unit, onZoomStep: (Int) -> Unit, modifier: Modifier = Modifier) {
    val label = zoomLabel(zoomFactor)
    MenuPill(
        label = label,
        description = "Zoom $label",
        entries = listOf(MenuEntry(FIT, TimelineCopy.fitProject), MenuEntry(IN, ZOOM_IN), MenuEntry(OUT, ZOOM_OUT)),
        selectedId = null,
        onSelect = { id ->
            when (id) {
                FIT -> onFitProject()
                IN -> onZoomStep(1)
                OUT -> onZoomStep(-1)
            }
        },
        testTag = TimelineTestTags.ZOOM,
        modifier = modifier,
    )
}

/** "1×", "2.5×", "16×": one decimal under ten, whole above. */
internal fun zoomLabel(factor: Double): String {
    val rounded = if (factor < 10.0) (factor * 10).roundToInt() / 10.0 else factor.roundToInt().toDouble()
    val text = if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
    return "$text×"
}

private const val FIT = "fit"
private const val IN = "in"
private const val OUT = "out"
private const val ZOOM_IN = "Zoom in"
private const val ZOOM_OUT = "Zoom out"
