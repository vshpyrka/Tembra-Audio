package audio.tembra.perf

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.tracing.trace
import audio.tembra.perf.journeys.TimelineJourney
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The timeline journey under frame, memory and section metrics (feature 009); a warm launch precedes each measured iteration. */
@RunWith(AndroidJUnit4::class)
class TimelineJourneyBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private val journey = TimelineJourney()

    @Test
    fun timelineJourney() = benchmarkRule.measureRepeated(
        packageName = PerfMetrics.PACKAGE,
        metrics = PerfMetrics.journey("timeline"),
        compilationMode = CompilationMode.Full(),
        startupMode = StartupMode.WARM,
        iterations = PerfMetrics.ITERATIONS,
        setupBlock = {
            pressHome()
            startActivityAndWait()
        },
    ) {
        trace("journey.timeline") { journey.run(device) }
    }
}
