package audio.tembra.ui.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.SubcomposeLayoutState
import androidx.compose.ui.layout.SubcomposeMeasureScope
import androidx.compose.ui.unit.Constraints
import audio.tembra.ui.theme.TembraTheme
import kotlinx.coroutines.yield
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * A [androidx.compose.foundation.layout.BoxWithConstraints] that prepares its content before it shows it. A screen
 * opened from the navigation used to compose, measure and draw whole in the frame after the tap: 28 to 56 ms for the
 * Metronome on a Pixel 6 Pro, two to three 60 Hz frames. Here the first frame only learns the [Constraints]; the
 * content is then composed for them a [SLICE] at a time between the following frames (a paused precomposition, the
 * mechanism lazy lists prefetch with), measured ahead, and only then placed, so no frame carries the whole screen.
 * Once placed it fades in with the theme's motion; the navigation leaves the entering fade to it (FadesItselfInKey), so
 * the fade starts when there is something to show rather than at the tap. Afterwards it
 * behaves like a BoxWithConstraints: new constraints recompose the content in the measure pass that brings them.
 */
@Composable
fun PreparedLayout(modifier: Modifier = Modifier, content: @Composable (Constraints) -> Unit) {
    val state = remember { SubcomposeLayoutState() }
    val latest by rememberUpdatedState(content)
    val slots = remember { PreparedSlots() }
    var shown by remember { mutableStateOf(false) }
    val motion = TembraTheme.motion
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(state) {
        val constraints = slots.awaitConstraints()
        state.prepare(slots.slotFor(constraints) { latest(it) }, constraints)
        shown = true
        alpha.animateTo(1f, tween(motion.effectiveDuration, easing = motion.standardEasing))
    }
    val faded = modifier.graphicsLayer {
        this.alpha = alpha.value
        compositingStrategy = FadeInCompositing
    }
    SubcomposeLayout(state, faded) { constraints ->
        slots.measured = constraints
        if (!shown) return@SubcomposeLayout layout(constraints.minWidth, constraints.minHeight) {}
        placePrepared(constraints, slots.slotFor(constraints) { latest(it) })
    }
}

/** Waits, a frame at a time, for the first measure pass to bring the constraints. */
internal suspend fun PreparedSlots.awaitConstraints(): Constraints {
    var constraints = measured
    while (constraints == null) {
        withFrameNanos { }
        constraints = measured
    }
    return constraints
}

/**
 * Composes [slot] a [SLICE] per frame, run after that frame's work (the yield leaves the frame callback), the way lazy
 * lists prefetch: the frames around it keep their turn, and each carries at most a slice besides its own work. Then it
 * measures the result for [constraints] in a turn of its own, so the frame that places it measures nothing new.
 */
internal suspend fun SubcomposeLayoutState.prepare(slot: @Composable () -> Unit, constraints: Constraints) {
    val paused = createPausedPrecomposition(SLOT_KEY, slot)
    while (!paused.isComplete) {
        withFrameNanos { }
        yield()
        val started = TimeSource.Monotonic.markNow()
        paused.resume { started.elapsedNow() > SLICE }
    }
    val handle = paused.apply()
    withFrameNanos { }
    yield()
    repeat(handle.placeablesCount) { handle.premeasure(it, constraints) }
}

/** Measures and places the prepared [slot] at the top start, as a BoxWithConstraints places its content. */
internal fun SubcomposeMeasureScope.placePrepared(constraints: Constraints, slot: @Composable () -> Unit): MeasureResult {
    val placeables = subcompose(SLOT_KEY, slot).map { it.measure(constraints) }
    val width = (placeables.maxOfOrNull { it.width } ?: 0).coerceIn(constraints.minWidth, constraints.maxWidth)
    val height = (placeables.maxOfOrNull { it.height } ?: 0).coerceIn(constraints.minHeight, constraints.maxHeight)
    return layout(width, height) { placeables.forEach { it.place(0, 0) } }
}

/**
 * The constraints the last measure pass brought and the slot content composed for them. Plain fields: the measure
 * pass writes them and the preparation reads them, and no composition reads them, so nothing is fed back across
 * phases. The same lambda for the same constraints is what lets the measure pass reuse the prepared slot as it is.
 */
internal class PreparedSlots {
    var measured: Constraints? = null
    private var slotConstraints: Constraints? = null
    private var slot: (@Composable () -> Unit)? = null

    fun slotFor(constraints: Constraints, content: @Composable (Constraints) -> Unit): @Composable () -> Unit {
        val current = slot
        if (current != null && constraints == slotConstraints) return current
        val created: @Composable () -> Unit = { content(constraints) }
        slot = created
        slotConstraints = constraints
        return created
    }
}

private const val SLOT_KEY = "prepared"

/**
 * How a fading layer composites: this fade-in and the status pill's pulsing dot. Android:
 * [CompositingStrategy.ModulateAlpha], the alpha applied to each draw, which spares the first frame a full-screen
 * offscreen layer (13 ms of a Pixel 6 Pro's render thread) and the dot a layer rendered afresh every pulse. iOS: the
 * default, because ModulateAlpha left the Metronome's play button undrawn there (Compose Multiplatform 1.12.1, iPhone 14).
 */
internal expect val FadeInCompositing: CompositingStrategy

/** The composition each frame lends to the preparation: a third of a 60 Hz frame, leaving the rest to the frame. */
private val SLICE = 5.milliseconds
