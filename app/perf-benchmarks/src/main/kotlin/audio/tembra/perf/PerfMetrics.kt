@file:OptIn(ExperimentalMetricApi::class)

package audio.tembra.perf

import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.Metric
import androidx.benchmark.macro.TraceSectionMetric

/**
 * The metrics every journey benchmark collects (contracts/metric-catalogue.md): frame timing, memory (peak RSS anon,
 * last heap size), the journey's own trace section (`journey.<name>`, emitted by the test process, hence not
 * target-package only) and the catalogue sections the journey exercises (`TraceSectionMetric(name, Sum)`).
 * Section names are the signpost names of `app/shared/engine/perf-catalogue.txt`.
 */
object PerfMetrics {
    const val PACKAGE = "com.tembraaudio"
    const val ITERATIONS = 20

    private val tunerSections = listOf("tuner.load", "tuner.modeSwitch", "tuner.frame")
    private val metronomeSections = listOf("metronome.load", "metronome.tempoChange")
    // Feature 009 (contracts/perf-instrumentation.md): the eleven sections the timeline journey exercises; the master
    // pop-up's (design iteration D1) reads 0 where the bar is wide enough for the inline slider.
    private val timelineSections = listOf(
        "timeline.projectOpen", "timeline.waveform", "transport.play", "transport.seek", "timeline.zoom", "timeline.scroll",
        "timeline.clipSplit", "timeline.playheadFrame", "engine.prime", "engine.loadClip", "transport.masterPopup",
    )
    // Feature 010 (contracts/perf-instrumentation.md): the recording sections and the timeline ones the journey shares.
    private val recordingSections = listOf(
        "timeline.arm", "transport.recordStart", "transport.countIn", "timeline.takeFrame", "transport.recordStop",
        "timeline.monitorToggle", "engine.takeAlignment", "timeline.waveform", "transport.play", "timeline.playheadFrame",
        "timeline.projectSave",
    )
    // Feature 011 (contracts/perf-instrumentation.md): the window changes, the right-click menu and the destination chords,
    // with the metronome and the timeline they run over.
    private val windowingSections = listOf(
        "shell.windowRelayout", "timeline.contextMenu", "shell.shortcutNavigate", "metronome.load", "timeline.projectOpen", "transport.play",
    )
    // Feature 012 (contracts/perf-instrumentation.md): the mixer's sections and the timeline ones the journey plays over.
    private val mixerSections = listOf(
        "timeline.mixerOpen", "timeline.mixerClose", "timeline.mixerFrame", "timeline.faderDrag", "timeline.panTurn",
        "transport.masterDrag", "engine.panApply", "transport.play", "timeline.playheadFrame", "timeline.projectSave",
    )
    private val stressSections = tunerSections + metronomeSections + listOf("timeline.scroll", "timeline.playheadFrame", "transport.play", "timeline.mixerFrame")

    fun journey(name: String): List<Metric> = listOf(
        FrameTimingMetric(),
        MemoryUsageMetric(MemoryUsageMetric.Mode.Max, listOf(MemoryUsageMetric.SubMetric.RssAnon)),
        MemoryUsageMetric(MemoryUsageMetric.Mode.Last, listOf(MemoryUsageMetric.SubMetric.RssAnon)),
        TraceSectionMetric("journey.$name", TraceSectionMetric.Mode.Sum, targetPackageOnly = false),
    ) + sectionsOf(name).map { TraceSectionMetric(it, TraceSectionMetric.Mode.Sum) }

    private fun sectionsOf(name: String): List<String> = when (name) {
        "tuner" -> tunerSections
        "metronome" -> metronomeSections
        "timeline" -> timelineSections
        "recording" -> recordingSections
        "stress" -> stressSections
        "windowing" -> windowingSections
        "mixer" -> mixerSections
        else -> emptyList()
    }
}
