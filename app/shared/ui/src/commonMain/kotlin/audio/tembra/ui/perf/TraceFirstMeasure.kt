package audio.tembra.ui.perf

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import audio.tembra.engine.perf.PerfSection
import audio.tembra.engine.perf.PerfTrace

/**
 * Wraps the first measure of this layout, and only that one, in `PerfTrace.section(section)`: the layout cost of a
 * surface appearing, such as a pop-up's card laying out its content when it opens. It runs in the layout phase, never
 * in composition (contracts/tracing-facade.md). Later measures, such as a drag that relays the card out, are not traced;
 * a new node (the surface shown again) traces its own first measure. Inert outside a measurement build.
 */
internal fun Modifier.traceFirstMeasure(section: PerfSection): Modifier = this then TraceFirstMeasureElement(section)

private data class TraceFirstMeasureElement(val section: PerfSection) : ModifierNodeElement<TraceFirstMeasureNode>() {
    override fun create() = TraceFirstMeasureNode(section)

    override fun update(node: TraceFirstMeasureNode) {
        node.section = section
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "traceFirstMeasure"
        properties["section"] = section
    }
}

private class TraceFirstMeasureNode(var section: PerfSection) : Modifier.Node(), LayoutModifierNode {
    private var measured = false

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = if (measured) {
            measurable.measure(constraints)
        } else {
            measured = true
            PerfTrace.section(section) { measurable.measure(constraints) }
        }
        return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
}
