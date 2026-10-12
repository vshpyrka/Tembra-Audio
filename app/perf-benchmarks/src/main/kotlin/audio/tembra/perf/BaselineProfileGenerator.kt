package audio.tembra.perf

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import audio.tembra.perf.journeys.MetronomeJourney
import audio.tembra.perf.journeys.StressJourney
import audio.tembra.perf.journeys.TunerJourney
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the app's baseline profile and startup profile from the pipeline's journeys (feature 008, D2; spec
 * FR-001/FR-002). Run only by `uv run tracepulse profile generate --device <id>`, which runs the plugin's
 * `:androidApp:generateBaselineProfile` task; the plugin filters the instrumentation to `BaselineProfileRule` tests
 * (`androidx.benchmark.enabledRules=BaselineProfile`), so the `MacrobenchmarkRule` classes stay out, and a round
 * never runs this class: `tracepulse run` starts one `am instrument -e class <journey class>` per journey.
 *
 * One `collect` per test, because the plugin finds a test's profile files through the per-test output message
 * (`additionaltestoutput.benchmark.message_<class>.<method>.txt`), which a second `collect` in the same test
 * overwrites (the first generation on 2026-10-12 lost its startup profile that way). [startup] collects the
 * cold-start path with `includeInStartupProfile = true` (what R8 lays out together, `startup-prof.txt`);
 * [journeys] collects the three journeys without it. `collect` keeps the rule's own `maxIterations` (15) and
 * `stableIterations` (3): it repeats until three consecutive iterations add no new rule, which is the "merged over
 * repetitions" the spec requires and makes regeneration on an unchanged app differ only in order and timing-reached
 * code. `collect` fails the test by itself when no rule was collected, so no assertion is written here.
 *
 * The plugin's task installs the app itself (and uninstalls it afterwards), so unlike a round, where the pipeline's
 * install step grants the microphone, the generator grants `RECORD_AUDIO` before collecting: without it the tuner
 * opens under the permission dialog and its anchor is never found (the second generation on 2026-10-12).
 *
 * A journey added to the pipeline is registered in [journeys] (and in [ProfileComparisonBenchmark]) once more; the
 * journey definition itself stays single (spec FR-002).
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Before
    fun grantMicrophone() {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            .executeShellCommand("pm grant ${PerfMetrics.PACKAGE} android.permission.RECORD_AUDIO")
    }

    @Test
    fun startup() = rule.collect(packageName = PerfMetrics.PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
    }

    @Test
    fun journeys() = rule.collect(packageName = PerfMetrics.PACKAGE, includeInStartupProfile = false) {
        pressHome()
        startActivityAndWait()
        TunerJourney().run(device)
        MetronomeJourney().run(device)
        StressJourney().run(device)
    }
}
