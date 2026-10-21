package audio.tembra.ui.designsystem

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.SubcomposeLayoutState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import audio.tembra.ui.theme.LocalTembraMotion
import audio.tembra.ui.theme.TembraMotion
import audio.tembra.ui.theme.TembraTheme
import kotlinx.coroutines.yield

/**
 * Draws [content] once ahead, at the constraints this layout is given, and then calls [onDrawn]. Nothing of it is seen,
 * read by a screen reader, focused or touched. The point is the GPU: the first frame that draws a screen's kinds of
 * shapes in a process builds their pipelines and rasterises their glyphs first (15 to 25 ms of a Pixel 6 Pro's render
 * thread, 7 ms of pipelines and 5 ms of glyphs on an iPhone 14), which made that frame late; drawn here while the app has
 * nothing else to draw, the screen's first visible frame costs what any other does. The content is prepared as
 * [PreparedLayout] prepares it, a slice per frame, then drawn a horizontal band per frame, so no frame carries more than
 * a band's first-use cost. Android renders the bands off the window ([DrawsAheadOffscreen]); iOS, whose layers render
 * on the CPU, draws them in the window beneath everything at [UNSEEN] alpha.
 */
@Composable
fun DrawAhead(onDrawn: () -> Unit, modifier: Modifier = Modifier, content: @Composable (Constraints) -> Unit) {
    val drawn by rememberUpdatedState(onDrawn)
    val state = remember { SubcomposeLayoutState() }
    val latest by rememberUpdatedState(content)
    val slots = remember { PreparedSlots() }
    val layer = rememberGraphicsLayer()
    val band = rememberGraphicsLayer()
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    var placed by remember { mutableStateOf(false) }
    // The band drawn in the window (iOS), or -1.
    var shownBand by remember { mutableIntStateOf(-1) }
    // Held still: no frame tick and no endless animation, so the content asks the window for no frame of its own.
    val still = TembraTheme.motion.copy(reduceMotion = true)
    LaunchedEffect(state) {
        val constraints = slots.awaitConstraints()
        state.prepare(slots.slotFor(constraints) { HeldStill(still) { latest(it) } }, constraints)
        placed = true
        // The next frame places the content (and records it, off the window); the one after takes whatever that placing
        // set off.
        withFrameNanos { }
        withFrameNanos { }
        if (DrawsAheadOffscreen) {
            // A band at a time, one after each frame's work: whole, the first render of a screen held the render thread
            // for 15 to 25 ms, and a frame of the launch that fell due meanwhile was late.
            val full = layer.size
            val height = (full.height + BANDS - 1) / BANDS
            for (index in 0 until BANDS) {
                val top = index * height
                if (full.width <= 0 || top >= full.height) break
                yield()
                band.record(density, direction, IntSize(full.width, minOf(height, full.height - top))) {
                    translate(top = -top.toFloat()) { drawLayer(layer) }
                }
                band.toImageBitmap()
                // A frame asked for during the render runs late; it draws nothing unless something changes first.
                withFrameNanos { }
            }
        } else {
            for (index in 0 until BANDS) {
                shownBand = index
                withFrameNanos { }
                withFrameNanos { }
            }
            shownBand = -1
            withFrameNanos { }
        }
        drawn()
    }
    val hidden = modifier
        .clearAndSetSemantics { }
        .focusProperties { onEnter = { cancelFocusChange() } }
        .focusGroup()
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        }
        .graphicsLayer { alpha = if (DrawsAheadOffscreen) 1f else UNSEEN }
        .drawWithContent {
            when {
                !placed -> Unit
                DrawsAheadOffscreen -> layer.record { this@drawWithContent.drawContent() }
                shownBand >= 0 -> {
                    val height = size.height / BANDS
                    clipRect(top = shownBand * height, bottom = (shownBand + 1) * height) { this@drawWithContent.drawContent() }
                }
            }
        }
    SubcomposeLayout(state, hidden) { constraints ->
        slots.measured = constraints
        if (!placed) return@SubcomposeLayout layout(constraints.minWidth, constraints.minHeight) {}
        placePrepared(constraints, slots.slotFor(constraints) { HeldStill(still) { latest(it) } })
    }
}

@Composable
private fun HeldStill(motion: TembraMotion, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalFrameTicksPaused provides true, LocalTembraMotion provides motion, content = content)
}

/** How many horizontal bands the content is drawn in, one per frame. */
private const val BANDS = 4

/** The lowest alpha a draw still happens at: Skia skips a draw whose alpha is zero. */
private const val UNSEEN = 1f / 255

/**
 * Where [DrawAhead] draws. Android: off the window, since a layer renders on the render thread's GPU context, the one
 * the window draws with. iOS: in the window, beneath everything, since a layer renders into a bitmap on the CPU and
 * would warm nothing the window uses.
 */
internal expect val DrawsAheadOffscreen: Boolean
