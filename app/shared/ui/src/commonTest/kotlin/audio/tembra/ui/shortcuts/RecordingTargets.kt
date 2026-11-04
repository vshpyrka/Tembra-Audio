package audio.tembra.ui.shortcuts

import audio.tembra.ui.navigation.Destination

/** Targets for every scope that record the action each call stands for (feature 011's dispatcher test). */
class RecordingTargets(private val record: (ShortcutAction) -> Unit) {
    private val shell = object : ShellShortcuts {
        override fun open(destination: Destination): Boolean {
            record(
                when (destination) {
                    Destination.Timeline -> ShortcutAction.OpenTimeline
                    Destination.Tuner -> ShortcutAction.OpenTuner
                    Destination.Metronome -> ShortcutAction.OpenMetronome
                    Destination.Projects -> ShortcutAction.OpenProjects
                    Destination.Profile -> ShortcutAction.OpenProfile
                },
            )
            return true
        }

        override fun showShortcuts(): Boolean = true.also { record(ShortcutAction.ShowShortcuts) }

        override fun closeOverlay(): Boolean = true.also { record(ShortcutAction.CloseOverlay) }
    }
    private val timeline = object : TimelineShortcuts {
        override fun playPause() = true.also { record(ShortcutAction.TimelinePlayPause) }
        override fun skipToStart() = true.also { record(ShortcutAction.TimelineSkipToStart) }
        override fun step(direction: Int, bar: Boolean) = true.also {
            record(
                when {
                    bar && direction < 0 -> ShortcutAction.TimelineBarBack
                    bar -> ShortcutAction.TimelineBarForward
                    direction < 0 -> ShortcutAction.TimelineStepBack
                    else -> ShortcutAction.TimelineStepForward
                },
            )
        }
        override fun split() = true.also { record(ShortcutAction.TimelineSplit) }
        override fun deleteClip() = true.also { record(ShortcutAction.TimelineDeleteClip) }
        override fun undo() = true.also { record(ShortcutAction.TimelineUndo) }
        override fun redo() = true.also { record(ShortcutAction.TimelineRedo) }
        override fun zoom(steps: Int) = true.also { record(if (steps > 0) ShortcutAction.TimelineZoomIn else ShortcutAction.TimelineZoomOut) }
        override fun toggleMute() = true.also { record(ShortcutAction.TimelineMute) }
        override fun toggleSolo() = true.also { record(ShortcutAction.TimelineSolo) }
        override fun arm() = true.also { record(ShortcutAction.TimelineArm) }
        override fun record() = true.also { this@RecordingTargets.record(ShortcutAction.TimelineRecord) }
        override fun toggleMonitor() = true.also { record(ShortcutAction.TimelineMonitor) }
    }
    private val metronome = object : MetronomeShortcuts {
        override fun startStop() = true.also { record(ShortcutAction.MetronomeStartStop) }
        override fun changeTempo(delta: Int) = true.also {
            record(
                when (delta) {
                    1 -> ShortcutAction.MetronomeTempoUp
                    -1 -> ShortcutAction.MetronomeTempoDown
                    10 -> ShortcutAction.MetronomeTempoUpTen
                    else -> ShortcutAction.MetronomeTempoDownTen
                },
            )
        }
        override fun tapTempo() = true.also { record(ShortcutAction.MetronomeTapTempo) }
    }
    private val tuner = object : TunerShortcuts {
        override fun autoListen() = true.also { record(ShortcutAction.TunerAutoListen) }
        override fun byEar() = true.also { record(ShortcutAction.TunerByEar) }
        override fun playString(number: Int) = true.also { record(ShortcutAction.entries.first { it.name == "TunerPlayString$number" }) }
        override fun stopTone() = true.also { record(ShortcutAction.TunerStopTone) }
        override fun toggleLoop() = true.also { record(ShortcutAction.TunerToggleLoop) }
    }
    private val projects = object : ProjectsShortcuts {
        override fun focusSearch() = true.also { record(ShortcutAction.ProjectsFocusSearch) }
        override fun newProject() = true.also { record(ShortcutAction.ProjectsNewProject) }
    }

    val all = ShortcutTargets(shell = shell, timeline = timeline, tuner = tuner, metronome = metronome, projects = projects)
}
