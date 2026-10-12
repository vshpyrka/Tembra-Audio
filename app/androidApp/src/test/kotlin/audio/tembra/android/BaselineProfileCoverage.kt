package audio.tembra.android

import java.io.File

/**
 * Reads the coverage rule (`baseline-profile-coverage.txt`) and the committed baseline profile and names what the
 * profile does not cover (feature 008, contracts/coverage-rule.md). Pure text work: descriptors are compared as the
 * profile format writes them, so no Android runtime and no reflection is involved.
 */
internal object BaselineProfileCoverage {
    const val RULES_FILE = "baseline-profile-coverage.txt"
    const val PROFILE_FILE = "src/main/generated/baselineProfiles/baseline-prof.txt"
    const val GENERATE_COMMAND = "uv run tracepulse profile generate --device <id>"

    /** `<label> <descriptor>` per line; blank lines and `#` comments are skipped; anything else is an error. */
    fun parseRules(lines: List<String>): List<CoverageRule> = lines.mapNotNull { raw ->
        val line = raw.trim()
        when {
            line.isEmpty() -> null
            line.startsWith("#") -> null
            else -> {
                val fields = line.split(Regex("\\s+"))
                require(fields.size == 2) { "$RULES_FILE: expected `<label> <descriptor>`, got `$line`" }
                CoverageRule(label = fields[0], descriptor = fields[1])
            }
        }
    }

    /**
     * The rules without a matching profile line, in rule order. A profile line matches a descriptor when, without its
     * leading `H`/`S`/`P` flags, it equals the descriptor (a class rule) or starts with it followed by `->` (a method
     * rule of that class). Extra profile lines never matter.
     */
    fun uncovered(rules: List<CoverageRule>, profileLines: List<String>): List<CoverageRule> {
        val entries = profileLines.map { it.trim().trimStart('H', 'S', 'P') }.filter { it.isNotEmpty() }
        return rules.filter { rule ->
            entries.none { it == rule.descriptor || it.startsWith(rule.descriptor + "->") }
        }
    }

    /** The failure message for the module at [moduleDir], or null when the committed profile covers every rule. */
    fun failure(moduleDir: File): String? {
        val rules = parseRules(File(moduleDir, RULES_FILE).readLines())
        val profile = File(moduleDir, PROFILE_FILE)
        val profileLines = if (profile.isFile) profile.readLines().filter { it.isNotBlank() } else emptyList()
        if (profileLines.isEmpty()) {
            return "baseline profile missing: run `$GENERATE_COMMAND` from app/perf"
        }
        val missing = uncovered(rules, profileLines)
        if (missing.isEmpty()) return null
        return "baseline profile does not cover: " + missing.joinToString { "${it.label} (${it.descriptor})" }
    }
}
