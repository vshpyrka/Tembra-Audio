package audio.tembra.ui.perf

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import audio.tembra.engine.perf.PerfSection
import audio.tembra.engine.perf.PerfTrace

/**
 * Wraps the shell's measure in `PerfTrace.section(SHELL_WINDOW_RELAYOUT)` whenever the window it is given crosses a
 * width class (600 and 840 dp) or the short-window line (480 dp) since the last measure: the relayout a window change
 * costs (feature 011, contracts/perf-instrumentation.md). Measures within a class, and the first one, are not traced.
 * Layout phase only; inert outside a measurement build.
 */
internal fun Modifier.traceWindowRelayout(): Modifier = this then TraceWindowRelayoutElement

private data object TraceWindowRelayoutElement : ModifierNodeElement<TraceWindowRelayoutNode>() {
    override fun create() = TraceWindowRelayoutNode()

    override fun update(node: TraceWindowRelayoutNode) = Unit

    override fun InspectorInfo.inspectableProperties() {
        name = "traceWindowRelayout"
    }
}

private class TraceWindowRelayoutNode : Modifier.Node(), LayoutModifierNode {
    private var lastClass = -1

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val sizeClass = sizeClass(constraints)
        val crossed = lastClass != -1 && sizeClass != lastClass
        lastClass = sizeClass
        val placeable = if (crossed) {
            PerfTrace.section(PerfSection.SHELL_WINDOW_RELAYOUT) { measurable.measure(constraints) }
        } else {
            measurable.measure(constraints)
        }
        return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

    private fun MeasureScope.sizeClass(constraints: Constraints): Int {
        if (!constraints.hasBoundedWidth || !constraints.hasBoundedHeight) return 0
        val width = constraints.maxWidth.toDp()
        val height = constraints.maxHeight.toDp()
        val widthClass = when {
            width < 600.dp -> 0
            width < 840.dp -> 1
            else -> 2
        }
        val shortClass = if (height < 480.dp) 0 else 1
        return widthClass * 2 + shortClass
    }
}
