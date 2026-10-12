package audio.tembra.android

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The host-lane coverage check of the committed baseline profile (feature 008, FR-011, contracts/coverage-rule.md):
 * the pure logic first, then the two files of this module read relative to its directory (Gradle's working
 * directory for Android unit tests). The last test is the gate the lane runs; it is red until the first profile is
 * generated and committed.
 */
class BaselineProfileCoverageTest {
    private val rules = BaselineProfileCoverage.parseRules(
        listOf(
            "# Startup path",
            "app.start    Laudio/tembra/android/TembraApplication;",
            "",
            "home         Laudio/tembra/ui/home/HomeScreenKt;",
            "tuner        Laudio/tembra/ui/tuner/TunerScreenKt;",
        ),
    )

    @Test
    fun ruleFileIgnoresBlankLinesAndComments() {
        assertEquals(listOf("app.start", "home", "tuner"), rules.map { it.label })
        assertEquals("Laudio/tembra/ui/home/HomeScreenKt;", rules[1].descriptor)
    }

    @Test
    fun emptyProfileLeavesEveryRuleUncovered() {
        assertEquals(rules, BaselineProfileCoverage.uncovered(rules, emptyList()))
    }

    @Test
    fun classRuleAndFlaggedMethodRuleCoverTheirDescriptors() {
        val profile = listOf(
            "Laudio/tembra/ui/tuner/TunerScreenKt;",
            "HSPLaudio/tembra/ui/home/HomeScreenKt;->HomeScreen(Landroidx/compose/runtime/Composer;I)V",
        )
        assertEquals(listOf("app.start"), BaselineProfileCoverage.uncovered(rules, profile).map { it.label })
    }

    @Test
    fun aDescriptorIsNotCoveredByALongerClassName() {
        // `HomeScreenKt;` must not be satisfied by `HomeScreenKt$lambda;` or by a descriptor that only shares a prefix.
        val profile = listOf("HSPLaudio/tembra/ui/home/HomeScreenKt\$HomeScreen\$1;->invoke()V")
        assertTrue(BaselineProfileCoverage.uncovered(rules, profile).any { it.label == "home" })
    }

    @Test
    fun missingProfileFailsNamingTheGenerationCommand() {
        val module = File.createTempFile("coverage", "").apply { delete(); mkdirs() }
        File(module, BaselineProfileCoverage.RULES_FILE).writeText("home Laudio/tembra/ui/home/HomeScreenKt;\n")
        val failure = BaselineProfileCoverage.failure(module)
        assertEquals(
            "baseline profile missing: run `uv run tracepulse profile generate --device <id>` from app/perf",
            failure,
        )
    }

    @Test
    fun uncoveredRulesAreListedInRuleFileOrder() {
        val module = File.createTempFile("coverage", "").apply { delete(); mkdirs() }
        File(module, BaselineProfileCoverage.RULES_FILE).writeText(
            "home   Laudio/tembra/ui/home/HomeScreenKt;\ntuner  Laudio/tembra/ui/tuner/TunerScreenKt;\n",
        )
        val profileDir = File(module, "src/main/generated/baselineProfiles").apply { mkdirs() }
        File(profileDir, "baseline-prof.txt").writeText("Landroidx/compose/runtime/Composer;\n")
        assertEquals(
            "baseline profile does not cover: home (Laudio/tembra/ui/home/HomeScreenKt;), " +
                "tuner (Laudio/tembra/ui/tuner/TunerScreenKt;)",
            BaselineProfileCoverage.failure(module),
        )
    }

    /** The lane gate (FR-011): the committed profile covers every rule of `baseline-profile-coverage.txt`. */
    @Test
    fun committedProfileCoversEveryRule() {
        assertNull(BaselineProfileCoverage.failure(File(".")))
    }
}
