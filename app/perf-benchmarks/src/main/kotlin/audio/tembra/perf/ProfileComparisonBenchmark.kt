package audio.tembra.perf

import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tracing.trace
import audio.tembra.perf.journeys.MetronomeJourney
import audio.tembra.perf.journeys.MixerJourney
import audio.tembra.perf.journeys.RecordingJourney
import audio.tembra.perf.journeys.StressJourney
import audio.tembra.perf.journeys.TimelineJourney
import audio.tembra.perf.journeys.TunerJourney
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val ITERATIONS_ARGUMENT = "tembra.compare.iterations"

/**
 * The four journeys of the pipeline measured under the optimisation state named by the instrumentation argument
 * `tembra.compare.state` (feature 008, D2/D6): run only by `uv run tracepulse profile compare`, once per state on a
 * fresh install, never by a round (`tracepulse run` names the four benchmark classes explicitly). The metrics are
 * the ones of the four benchmark classes, so the collector keys the results by method name (`startup`, `tuner`,
 * `metronome`, `stress`) and produces the same catalogue keys as a round.
 *
 * The state is parsed as the first statement of every test, so a missing or unknown argument fails the test with the
 * accepted values before anything is measured. The iteration count comes from `tembra.compare.iterations` (default
 * [PerfMetrics.ITERATIONS]): Macrobenchmark 1.5 does not honour `androidx.benchmark.iterations` for
 * `measureRepeated` (checked on the Pixel 6 Pro on 2026-10-12). A journey added to the pipeline is registered here
 * and in [BaselineProfileGenerator] as well; the journey definition itself stays single (spec FR-002).
 */
@RunWith(AndroidJUnit4::class)
class ProfileComparisonBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private fun state(): CompareState =
        CompareState.parse(InstrumentationRegistry.getArguments().getString(CompareState.ARGUMENT))

    private fun iterations(): Int =
        InstrumentationRegistry.getArguments().getString(ITERATIONS_ARGUMENT)?.toInt() ?: PerfMetrics.ITERATIONS

    @Test
    fun startup() {
        val state = state()
        benchmarkRule.measureRepeated(
            packageName = PerfMetrics.PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = state.compilationMode(),
            startupMode = StartupMode.COLD,
            iterations = iterations(),
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
            iterations = iterations(),
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
            iterations = iterations(),
            setupBlock = {
                pressHome()
                startActivityAndWait()
            },
        ) {
            trace("journey.metronome") { journey.run(device) }
        }
    }

    @Test
    fun timeline() {
        val state = state()
        val journey = TimelineJourney()
        benchmarkRule.measureRepeated(
            packageName = PerfMetrics.PACKAGE,
            metrics = PerfMetrics.journey("timeline"),
            compilationMode = state.compilationMode(),
            startupMode = StartupMode.WARM,
            iterations = iterations(),
            setupBlock = {
                pressHome()
                startActivityAndWait()
            },
        ) {
            trace("journey.timeline") { journey.run(device) }
        }
    }

    @Test
    fun recording() {
        val state = state()
        val journey = RecordingJourney()
        benchmarkRule.measureRepeated(
            packageName = PerfMetrics.PACKAGE,
            metrics = PerfMetrics.journey("recording"),
            compilationMode = state.compilationMode(),
            startupMode = StartupMode.WARM,
            iterations = iterations(),
            setupBlock = {
                pressHome()
                startActivityAndWait()
            },
        ) {
            trace("journey.recording") { journey.run(device) }
        }
    }

    @Test
    fun mixer() {
        val state = state()
        val journey = MixerJourney()
        benchmarkRule.measureRepeated(
            packageName = PerfMetrics.PACKAGE,
            metrics = PerfMetrics.journey("mixer"),
            compilationMode = state.compilationMode(),
            startupMode = StartupMode.WARM,
            iterations = iterations(),
            setupBlock = {
                pressHome()
                startActivityAndWait()
            },
        ) {
            trace("journey.mixer") { journey.run(device) }
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
            iterations = iterations(),
            setupBlock = {
                pressHome()
                startActivityAndWait()
            },
        ) {
            trace("journey.stress") { journey.run(device) }
        }
    }
}
