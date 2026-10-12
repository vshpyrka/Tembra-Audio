package audio.tembra.perf

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode

/**
 * The optimisation state `tracepulse profile compare` measures one build in (feature 008, data-model.md): each
 * value is entered from a fresh install and maps to the Macrobenchmark [CompilationMode] that produces it. Rounds
 * never use this type: the four benchmark classes stay on `CompilationMode.Full()` (spec FR-010).
 */
enum class CompareState(val argument: String) {
    /** Compilation reset; nothing optimised ahead of time: the worst case after a direct install. */
    None("none"),

    /** The shipped profile installed and compiled ahead of time; the state fails when the APK carries none. */
    Profile("profile"),

    /** Everything compiled ahead of time: what the rounds measure, a reference only. */
    Full("full");

    fun compilationMode(): CompilationMode = when (this) {
        None -> CompilationMode.None()
        Profile -> CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require, warmupIterations = 0)
        Full -> CompilationMode.Full()
    }

    companion object {
        const val ARGUMENT = "tembra.compare.state"

        /**
         * The state named by the instrumentation argument `tembra.compare.state`, read by each test of
         * [ProfileComparisonBenchmark] as its first statement through `InstrumentationRegistry.getArguments()`.
         * A missing or unknown value fails the test with the message listing `none, profile, full`, so a typo in
         * the command never measures the wrong state (verified on the device: tasks T034/T039).
         */
        fun parse(argument: String?): CompareState =
            entries.firstOrNull { it.argument == argument }
                ?: throw IllegalArgumentException(
                    "$ARGUMENT must be one of ${entries.joinToString { it.argument }}, got ${argument ?: "nothing"}",
                )
    }
}
