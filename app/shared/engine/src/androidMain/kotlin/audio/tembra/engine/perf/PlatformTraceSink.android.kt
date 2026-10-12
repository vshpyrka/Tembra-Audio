package audio.tembra.engine.perf

import android.os.Build
import android.os.Trace

/**
 * Android system tracing: sections become `slice` rows Macrobenchmark's `TraceSectionMetric` reads; counters become
 * counter tracks on API 29 and above (minSdk is 26; below 29 they are no-ops).
 */
internal actual val platformTraceSink: PerfTraceSink = object : PerfTraceSink {
    override fun begin(category: String, name: String) = Trace.beginSection(name)
    override fun end(category: String, name: String) = Trace.endSection()
    override fun animationBegin(category: String, name: String) = Trace.beginSection(name)
    override fun animationEnd(category: String, name: String) = Trace.endSection()
    override fun counter(name: String, value: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Trace.setCounter(name, value)
    }
}
