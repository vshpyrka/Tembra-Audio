package audio.tembra.engine.perf

import android.os.Build
import android.os.Trace

/**
 * Android system tracing: sections become `slice` rows Macrobenchmark's `TraceSectionMetric` reads; counters become
 * counter tracks on API 29 and above (minSdk is 26; below 29 they are no-ops). Animation intervals are async sections
 * from API 29 (a process-level slice that may span suspensions and threads: the count-in of feature 010 waits for the
 * engine's downbeat inside its interval, which a thread-local section would mis-pair with whatever section the
 * thread opens meanwhile); below 29 they fall back to plain sections.
 */
internal actual val platformTraceSink: PerfTraceSink = object : PerfTraceSink {
    override fun begin(category: String, name: String) = Trace.beginSection(name)
    override fun end(category: String, name: String) = Trace.endSection()
    override fun animationBegin(category: String, name: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Trace.beginAsyncSection(name, ANIMATION_COOKIE) else Trace.beginSection(name)
    }
    override fun animationEnd(category: String, name: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Trace.endAsyncSection(name, ANIMATION_COOKIE) else Trace.endSection()
    }
    override fun counter(name: String, value: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Trace.setCounter(name, value)
    }
}

/** One interval per animation name at a time: the cookie that pairs its begin and end. */
private const val ANIMATION_COOKIE = 0
