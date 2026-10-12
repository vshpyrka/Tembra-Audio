package audio.tembra.engine.perf

/**
 * The tracing facade (spec FR-012, contracts/tracing-facade.md): named sections, an animation variant and counters,
 * recorded by each platform's system tracing under the catalogue's names. Every entry is inert outside a measurement
 * build (`PerfBuild.PERF_ENABLED` is a compile-time constant), and `section`/`animation` are inline so the lambda
 * allocates nothing. Sections wrap non-composable work only (ViewModel intents, draw-lambda computations, loaders),
 * never composition, and nothing here is ever called from the audio callback (the callback is C++ and never
 * includes `tembra_trace.h`).
 */
object PerfTrace {
    const val UI = "ui"
    const val COUNTER = "counter"

    /** A recording sink installed by tests in place of the platform binding. */
    internal var sinkForTest: PerfTraceSink? = null

    @PublishedApi
    internal val sink: PerfTraceSink
        get() = sinkForTest ?: platformTraceSink

    inline fun <T> section(section: PerfSection, block: () -> T): T {
        if (!PerfBuild.PERF_ENABLED) return block()
        val target = sink
        val name = section.signpostName()
        target.begin(UI, name)
        try {
            return block()
        } finally {
            target.end(UI, name)
        }
    }

    /** An animation interval (iOS attributes hitches to it; Android records a plain section). */
    inline fun <T> animation(section: PerfSection, block: () -> T): T {
        if (!PerfBuild.PERF_ENABLED) return block()
        val target = sink
        val name = section.signpostName()
        target.animationBegin(UI, name)
        try {
            return block()
        } finally {
            target.animationEnd(UI, name)
        }
    }

    fun counter(counter: PerfCounter, value: Long) {
        if (!PerfBuild.PERF_ENABLED) return
        sink.counter(counter.signpostName(), value)
    }
}
