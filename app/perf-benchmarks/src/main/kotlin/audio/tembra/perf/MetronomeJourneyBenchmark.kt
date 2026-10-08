package audio.tembra.perf

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.tracing.trace
import audio.tembra.perf.journeys.MetronomeJourney
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The metronome journey under frame, memory and section metrics; a warm launch precedes each measured iteration. */
@RunWith(AndroidJUnit4::class)
class MetronomeJourneyBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private val journey = MetronomeJourney()

    @Test
    fun metronomeJourney() = benchmarkRule.measureRepeated(
        packageName = PerfMetrics.PACKAGE,
        metrics = PerfMetrics.journey("metronome"),
        compilationMode = CompilationMode.Full(),
        startupMode = StartupMode.WARM,
        iterations = PerfMetrics.ITERATIONS,
        setupBlock = {
            pressHome()
            startActivityAndWait()
        },
    ) {
        trace("journey.metronome") { journey.run(device) }
    }
}
