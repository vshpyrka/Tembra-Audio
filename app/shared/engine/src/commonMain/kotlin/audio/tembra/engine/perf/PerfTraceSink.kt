package audio.tembra.engine.perf

/**
 * Where [PerfTrace] sends its intervals and counters: the platform binding ([platformTraceSink]: Android system
 * tracing, iOS `os_signpost` through the engine's C shim) or a recording double in tests. Categories are `ui` for
 * Kotlin sections and `counter` for counters; names are catalogue signpost names.
 */
@PublishedApi
internal interface PerfTraceSink {
    fun begin(category: String, name: String)
    fun end(category: String, name: String)
    fun animationBegin(category: String, name: String)
    fun animationEnd(category: String, name: String)
    fun counter(name: String, value: Long)
}

/** The platform's binding; the one platform detail behind the common facade. */
internal expect val platformTraceSink: PerfTraceSink
