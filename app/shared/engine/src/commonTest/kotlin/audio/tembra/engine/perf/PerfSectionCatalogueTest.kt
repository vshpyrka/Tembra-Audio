package audio.tembra.engine.perf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The catalogue (`app/shared/engine/perf-catalogue.txt`) is the single source of signpost names (spec FR-012):
 * every [PerfSection] and [PerfCounter] entry has exactly one line, names are unique and of the form
 * `<family>.<camelCase>` with the family matching the entry's [PerfFamily] (`counter` for counters), and outside a
 * measurement build every generated `signpostName()` is `""` (FR-014). The Gradle task `generatePerfBuild` writes
 * [PerfCatalogueExpectation] from the same file for this test run; `generateTraceNames` keeps the C header in step.
 */
class PerfSectionCatalogueTest {

    private val lines = PerfCatalogueExpectation.LINES.map { line ->
        val fields = line.split(' ')
        assertEquals(3, fields.size, "catalogue line '$line' must be <ENUM_NAME> <family> <signpost.name>")
        Triple(fields[0], fields[1], fields[2])
    }

    @Test
    fun everySectionHasExactlyOneLineWithItsFamily() {
        for (section in PerfSection.entries) {
            val matching = lines.filter { it.first == section.name }
            assertEquals(1, matching.size, "${section.name} must have exactly one catalogue line")
            val (_, family, name) = matching.single()
            assertEquals(familyKeyword(section.family), family, "${section.name} family")
            assertTrue(name.startsWith("$family."), "${section.name}: '$name' must start with '$family.'")
            assertTrue(Regex("[a-z]+\\.[a-z][A-Za-z0-9]*").matches(name), "${section.name}: '$name' is not <family>.<camelCase>")
        }
    }

    @Test
    fun everyCounterHasExactlyOneLineInTheCounterFamily() {
        for (counter in PerfCounter.entries) {
            val matching = lines.filter { it.first == counter.name }
            assertEquals(1, matching.size, "${counter.name} must have exactly one catalogue line")
            val (_, family, name) = matching.single()
            assertEquals("counter", family)
            assertTrue(name.startsWith("audio."), "${counter.name}: '$name' must start with 'audio.'")
        }
    }

    @Test
    fun catalogueHasNoStrayLinesAndUniqueNames() {
        val known = PerfSection.entries.map { it.name } + PerfCounter.entries.map { it.name }
        assertEquals(known.sorted(), lines.map { it.first }.sorted(), "every catalogue line names an enum entry")
        val names = lines.map { it.third }
        assertEquals(names.size, names.toSet().size, "signpost names are unique")
    }

    @Test
    fun signpostNamesFollowTheBuild() {
        for (section in PerfSection.entries) {
            val expected = if (PerfBuild.PERF_ENABLED) lines.single { it.first == section.name }.third else ""
            assertEquals(expected, section.signpostName(), section.name)
        }
        for (counter in PerfCounter.entries) {
            val expected = if (PerfBuild.PERF_ENABLED) lines.single { it.first == counter.name }.third else ""
            assertEquals(expected, counter.signpostName(), counter.name)
        }
    }

    /** Feature 010 (contracts/perf-instrumentation.md): the seven sections and four counters of recording. */
    @Test
    fun theRecordingSectionsAndCountersAreCatalogued() {
        val expected = mapOf(
            "TIMELINE_ARM" to "timeline.arm", "TRANSPORT_RECORD_START" to "transport.recordStart", "TRANSPORT_COUNT_IN" to "transport.countIn",
            "TIMELINE_TAKE_FRAME" to "timeline.takeFrame", "TRANSPORT_RECORD_STOP" to "transport.recordStop",
            "TIMELINE_MONITOR_TOGGLE" to "timeline.monitorToggle", "ENGINE_TAKE_ALIGNMENT" to "engine.takeAlignment",
            "AUDIO_INPUT_OVERRUNS" to "audio.inputOverruns", "AUDIO_RECORD_BACKLOG" to "audio.recordBacklog",
            "AUDIO_TAKE_ALIGNMENT_MS" to "audio.takeAlignmentMs", "AUDIO_MONITOR_DELAY_MS" to "audio.monitorDelayMs",
        )
        for ((entry, name) in expected) {
            assertEquals(name, lines.single { it.first == entry }.third, entry)
        }
        assertEquals(27, PerfSection.entries.size)
        assertEquals(13, PerfCounter.entries.size)
        assertEquals(40, lines.size)
    }

    /** Exhaustive by construction: a new family must choose its catalogue keyword here. */
    private fun familyKeyword(family: PerfFamily): String = when (family) {
        PerfFamily.TUNER -> "tuner"
        PerfFamily.METRONOME -> "metronome"
        PerfFamily.TIMELINE -> "timeline"
        PerfFamily.TRANSPORT -> "transport"
        PerfFamily.ENGINE -> "engine"
    }
}
