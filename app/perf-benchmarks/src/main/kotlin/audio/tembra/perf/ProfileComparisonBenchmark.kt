package audio.tembra.perf

import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tracing.trace
import audio.tembra.perf.journeys.MetronomeJourney
import audio.tembra.perf.journeys.StressJourney
import audio.tembra.perf.journeys.TunerJourney
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The four journeys of the pipeline measured under the optimisation state named by the instrumentation argument
 * `tembra.compare.state` (feature 008, D2/D6): run only by `uv run tracepulse profile compare`, once per state on a
 * fresh install, never by a round (`tracepulse run` names the four benchmark classes explicitly). The metrics are
 * the ones of the four benchmark classes, so the collector keys the results by method name (`startup`, `tuner`,
 * `metronome`, `stress`) and produces the same catalogue keys as a round.
 *
 * The state is parsed as the first statement of every test, so a missing or unknown argument fails the test with the
 * accepted values before anything is measured. A journey added to the pipeline is registered here and in
 * [BaselineProfileGenerator] as well; the journey definition itself stays single (spec FR-002).
 */
@RunWith(AndroidJUnit4::class)
class ProfileComparisonBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private fun state(): CompareState =
        CompareState.parse(InstrumentationRegistry.getArguments().getString(CompareState.ARGUMENT))

    @Test
    fun startup() {
        val state = state()
        benchmarkRule.measureRepeated(
            packageName = PerfMetrics.PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = state.compilationMode(),
            startupMode = StartupMode.COLD,
            iterations = PerfMetrics.ITERATIONS,
        ) {
            pressHome()
            startActivityAndWait()
        }
    }

    @Test
    fun tuner() {
        val state = state()
        val journey = TunerJourney()
        benchmarkRule.measureRepeated(
            packageName = PerfMetrics.PACKAGE,
            metrics = PerfMetrics.journey("tuner"),
            compilationMode = state.compilationMode(),
            startupMode = StartupMode.WARM,
            iterations = PerfMetrics.ITERATIONS,
            setupBlock = {
                pressHome()
                startActivityAndWait()
            },
        ) {
            trace("journey.tuner") { journey.run(device) }
        }
    }

    @Test
    fun metronome() {
        val state = state()
        val journey = MetronomeJourney()
        benchmarkRule.measureRepeated(
            packageName = PerfMetrics.PACKAGE,
            metrics = PerfMetrics.journey("metronome"),
            compilationMode = state.compilationMode(),
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

    @Test
    fun stress() {
        val state = state()
        val journey = StressJourney()
        benchmarkRule.measureRepeated(
            packageName = PerfMetrics.PACKAGE,
            metrics = PerfMetrics.journey("stress"),
            compilationMode = state.compilationMode(),
            startupMode = StartupMode.WARM,
            iterations = PerfMetrics.ITERATIONS,
            setupBlock = {
                pressHome()
                startActivityAndWait()
            },
        ) {
            trace("journey.stress") { journey.run(device) }
        }
    }
}
