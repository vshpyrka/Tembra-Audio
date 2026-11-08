package audio.tembra.perf

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.tracing.trace
import audio.tembra.perf.journeys.MixerJourney
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The mixer journey under frame, memory and section metrics (feature 012); a warm launch precedes each measured iteration. */
@RunWith(AndroidJUnit4::class)
class MixerJourneyBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private val journey = MixerJourney()

    @Test
    fun mixerJourney() = benchmarkRule.measureRepeated(
        packageName = PerfMetrics.PACKAGE,
        metrics = PerfMetrics.journey("mixer"),
        compilationMode = CompilationMode.Full(),
        startupMode = StartupMode.WARM,
        iterations = PerfMetrics.ITERATIONS,
        setupBlock = {
            pressHome()
            startActivityAndWait()
        },
    ) {
        trace("journey.mixer") { journey.run(device) }
    }
}
