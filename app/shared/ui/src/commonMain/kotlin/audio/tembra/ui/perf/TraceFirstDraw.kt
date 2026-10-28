package audio.tembra.ui.perf

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import audio.tembra.engine.perf.PerfSection
import audio.tembra.engine.perf.PerfTrace

/**
 * Wraps the first draw of this node, and only that one, in `PerfTrace.section(section)` (feature 010,
 * contracts/perf-instrumentation.md): the cost of a draw-phase surface appearing, such as the input meter's first
 * frame after a track is armed. The sibling of [traceFirstMeasure] for draw-only components that never relay out. It
 * runs in the draw phase, never in composition; later draws are not traced; a new node traces its own first draw.
 * Inert outside a measurement build.
 */
internal fun Modifier.traceFirstDraw(section: PerfSection): Modifier = this then TraceFirstDrawElement(section)

private data class TraceFirstDrawElement(val section: PerfSection) : ModifierNodeElement<TraceFirstDrawNode>() {
    override fun create() = TraceFirstDrawNode(section)

    override fun update(node: TraceFirstDrawNode) {
        node.section = section
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "traceFirstDraw"
        properties["section"] = section
    }
}

private class TraceFirstDrawNode(var section: PerfSection) : Modifier.Node(), DrawModifierNode {
    private var drawn = false

    override fun ContentDrawScope.draw() {
        if (drawn) {
            drawContent()
        } else {
            drawn = true
            PerfTrace.section(section) { drawContent() }
        }
    }
}
