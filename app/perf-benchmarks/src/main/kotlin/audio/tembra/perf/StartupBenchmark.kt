package audio.tembra.perf

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Cold startup: time to initial display and to full display (`ReportFirstContentDrawn` on Home), 20 iterations. */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupCold() = benchmarkRule.measureRepeated(
        packageName = PerfMetrics.PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.Full(),
        startupMode = StartupMode.COLD,
        iterations = PerfMetrics.ITERATIONS,
    ) {
        pressHome()
        startActivityAndWait()
    }
}
