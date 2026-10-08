package audio.tembra.engine.perf

import audio.tembra.engine.cinterop.tembra_trace_animation_begin
import audio.tembra.engine.cinterop.tembra_trace_animation_end
import audio.tembra.engine.cinterop.tembra_trace_begin
import audio.tembra.engine.cinterop.tembra_trace_counter
import audio.tembra.engine.cinterop.tembra_trace_end

/**
 * `os_signpost` through the engine's C shim (`tembra_trace.h`): `XCTOSSignpostMetric(subsystem: "audio.tembra",
 * category:, name:)` measures one section by its catalogue name.
 */
internal actual val platformTraceSink: PerfTraceSink = object : PerfTraceSink {
    override fun begin(category: String, name: String) = tembra_trace_begin(category, name)
    override fun end(category: String, name: String) = tembra_trace_end(category, name)
    override fun animationBegin(category: String, name: String) = tembra_trace_animation_begin(category, name)
    override fun animationEnd(category: String, name: String) = tembra_trace_animation_end(category, name)
    override fun counter(name: String, value: Long) = tembra_trace_counter(name, value)
}
