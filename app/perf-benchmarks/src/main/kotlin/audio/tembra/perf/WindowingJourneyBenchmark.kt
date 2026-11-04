package audio.tembra.perf

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.tracing.trace
import audio.tembra.perf.journeys.WindowingJourney
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The windowing journey under frame, memory and section metrics (feature 011); a warm launch precedes each measured iteration. */
@RunWith(AndroidJUnit4::class)
class WindowingJourneyBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private val journey = WindowingJourney()

    @Test
    fun windowingJourney() = benchmarkRule.measureRepeated(
        packageName = PerfMetrics.PACKAGE,
        metrics = PerfMetrics.journey("windowing"),
        compilationMode = CompilationMode.Full(),
        startupMode = StartupMode.WARM,
        iterations = PerfMetrics.ITERATIONS,
        setupBlock = {
            pressHome()
            startActivityAndWait()
        },
    ) {
        trace("journey.windowing") { journey.run(device) }
    }
}
