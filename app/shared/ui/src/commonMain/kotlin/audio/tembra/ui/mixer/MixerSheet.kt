package audio.tembra.ui.mixer

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import audio.tembra.engine.MixSnapshot
import audio.tembra.engine.perf.PerfSection
import audio.tembra.engine.perf.PerfTrace
import audio.tembra.ui.designsystem.rememberFrameTick
import audio.tembra.ui.perf.MeterFrameCounter
import audio.tembra.ui.session.InputTelemetry
import audio.tembra.ui.session.MixTelemetry
import audio.tembra.ui.shortcuts.RegisterOverlay
import audio.tembra.ui.theme.TembraShapes
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.TimelineCallbacks
import kotlinx.coroutines.flow.drop
import kotlin.math.roundToInt

/**
 * The mixer sheet over the lanes box (feature 012, spec FR-016 to FR-022; research R1; contracts/mixer-ui.md
 * "MixerSheet"): the handle row, the strip row and the pinned master on the raised surface, sliding up from the box's
 * bottom edge. `anchoredDraggable` holds its position over the anchors Hidden, Half and Full (landscape has no Half)
 * from [MixerUi.anchors]; the offset is read in the layout phase only, where the sheet's visible height follows it so
 * the strips always fit what shows. A keyed effect animates to the height the state asks for (a snap under reduce
 * motion); a settled drag reports [TimelineCallbacks.onMixerHeight] or, at Hidden, [TimelineCallbacks.onMixerToggle].
 * Back (a `NavigationBackHandler` registered inside the Timeline entry, so it outranks the shell's back stack while the
 * sheet is open) and the shell's close-overlay action (Escape, ⌘.) close it. Composed while open or still animating
 * shut; otherwise it takes no space. `paneTitle` "Mixer", tag `mixer.sheet`.
 *
 * The meters (research R6; spec FR-015): the root's `drawWithContent` reads [telemetry] once per frame into one shared
 * snapshot inside the `timeline.mixerFrame` section and then draws the strips, whose meters and dials read that
 * snapshot in their own draw lambdas through the frame tick; nothing recomposes per frame. A 10 Hz mirror of the
 * snapshot feeds the meters' semantics; the armed strip's meter follows [inputTelemetry] instead (FR-014). Each drawn
 * frame is reported to the shared `MeterFrameCounter`.
 */
@Composable
internal fun MixerSheet(
    ui: MixerUi,
    callbacks: TimelineCallbacks,
    telemetry: MixTelemetry,
    inputTelemetry: InputTelemetry,
    lanesHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val motion = TembraTheme.motion
    val daw = TembraTheme.daw
    val spacing = TembraTheme.spacing
    val lanesPx = with(density) { lanesHeight.toPx() }
    val anchors = remember(lanesPx, ui.anchors) {
        val halfPx = with(density) { ui.anchors.half.toPx() }
        val fullPx = with(density) { ui.anchors.full.toPx() }
        DraggableAnchors {
            MixerSheetValue.Hidden at lanesPx
            if (!ui.anchors.singleAnchor) MixerSheetValue.Half at lanesPx - halfPx
            MixerSheetValue.Full at lanesPx - fullPx
        }
    }
    val state = remember { AnchoredDraggableState(initialValue = MixerSheetValue.Hidden, anchors = anchors) }
    val target = when {
        !ui.open -> MixerSheetValue.Hidden
        ui.anchors.singleAnchor -> MixerSheetValue.Full
        else -> MixerSheetValue.of(ui.height)
    }
    val currentUi by rememberUpdatedState(ui)
    val currentCallbacks by rememberUpdatedState(callbacks)
    // A layout change (a rotation, a resized window) recomputes the anchors and moves the sheet to the asked height at once.
    var appliedAnchors by remember { mutableStateOf(anchors) }
    LaunchedEffect(anchors) {
        if (anchors !== appliedAnchors) {
            state.updateAnchors(anchors, newTarget = target)
            appliedAnchors = anchors
        }
    }
    LaunchedEffect(target, anchors, motion.reduceMotion) {
        if (!state.anchors.hasPositionFor(target)) return@LaunchedEffect
        if (motion.reduceMotion) state.snapTo(target) else state.animateTo(target, tween(motion.standardDuration, easing = motion.standardEasing))
    }
    // A settled drag writes back: the new height, or the close at Hidden (the first emission is the initial value).
    LaunchedEffect(state) {
        snapshotFlow { state.settledValue }.drop(1).collect { settled ->
            val now = currentUi
            when (settled) {
                MixerSheetValue.Hidden -> if (now.open) currentCallbacks.onMixerToggle()
                MixerSheetValue.Half -> if (now.open && now.height != MixerHeight.Half) currentCallbacks.onMixerHeight(MixerHeight.Half)
                MixerSheetValue.Full -> if (now.open && now.height != MixerHeight.Full) currentCallbacks.onMixerHeight(MixerHeight.Full)
            }
        }
    }
    // The frame loop ends with the sheet: the next open starts a new run of the dropped-frame counter.
    LaunchedEffect(ui.open) {
        if (!ui.open) MeterFrameCounter.shared.pause()
    }
    val shown = ui.open || state.currentValue != MixerSheetValue.Hidden || state.isAnimationRunning
    if (!shown) return
    if (ui.open) {
        NavigationBackHandler(state = rememberNavigationEventState(NavigationEventInfo.None), onBackCompleted = { callbacks.onMixerToggle() })
        RegisterOverlay { callbacks.onMixerToggle() }
    }
    // The meters' feed (research R6): one snapshot per sheet, the frame tick while open, the 10 Hz mirror for semantics.
    val levels = remember { MixSnapshot() }
    val tick = rememberFrameTick(active = ui.open)
    val armedIndex = ui.strips.firstOrNull { it.meterSource == MeterSource.Input }?.index
    val mirror = rememberMixerLevelMirror(levels, active = ui.open, armedIndex = armedIndex, inputTelemetry = inputTelemetry)
    val feed = remember(levels, tick, mirror, inputTelemetry) { MeterFeed(levels, tick, mirror, inputTelemetry) }
    val fling = AnchoredDraggableDefaults.flingBehavior(
        state = state,
        positionalThreshold = { distance -> distance * MixerSheetDefaults.POSITIONAL_THRESHOLD },
        animationSpec = tween(motion.effectiveDuration, easing = motion.standardEasing),
    )
    val border = daw.surfaceLevel2Border
    val borderWidth = spacing.seam
    Box(
        modifier = modifier
            .fillMaxSize()
            .layout { measurable, constraints ->
                // The layout phase reads the drag offset: the sheet's visible height follows it, nothing recomposes.
                val offset = if (state.offset.isNaN()) lanesPx else state.requireOffset()
                val visible = (lanesPx - offset).roundToInt().coerceIn(0, constraints.maxHeight)
                val placeable = measurable.measure(constraints.copy(minHeight = visible, maxHeight = visible))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.placeRelative(0, (constraints.maxHeight - visible).coerceAtLeast(0))
                }
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .testTag(MixerTestTags.SHEET)
                .semantics { paneTitle = MixerCopy.mixer }
                // One frame of the mixer: the one telemetry read, then every strip's draw (contracts/perf-instrumentation.md).
                .drawWithContent {
                    PerfTrace.section(PerfSection.TIMELINE_MIXER_FRAME) {
                        val frameNanos = tick.longValue
                        telemetry.read(frameNanos, levels)
                        MeterFrameCounter.shared.frame(frameNanos)
                        drawContent()
                    }
                }
                .anchoredDraggable(state = state, orientation = Orientation.Vertical, flingBehavior = fling)
                .background(daw.surfaceLevel2, RoundedCornerShape(topStart = TembraShapes.large, topEnd = TembraShapes.large))
                .drawBehind { drawRect(border, topLeft = Offset.Zero, size = Size(size.width, borderWidth.toPx())) },
        ) {
            MixerHandle(
                height = ui.height,
                onToggleHeight = if (ui.anchors.singleAnchor) {
                    null
                } else {
                    { callbacks.onMixerHeight(if (ui.height == MixerHeight.Half) MixerHeight.Full else MixerHeight.Half) }
                },
            )
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                MixerStripRow(strips = ui.strips, form = ui.form, selectedIndex = ui.selectedIndex, callbacks = callbacks, feed = feed, modifier = Modifier.weight(1f), canReorder = ui.canReorder)
                MasterStrip(master = ui.master, callbacks = callbacks, feed = feed, modifier = Modifier.padding(end = spacing.pageMargin))
            }
        }
    }
}
