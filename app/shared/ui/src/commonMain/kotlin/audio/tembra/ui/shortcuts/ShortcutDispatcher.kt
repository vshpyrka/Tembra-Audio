package audio.tembra.ui.shortcuts

import audio.tembra.engine.perf.PerfSection
import audio.tembra.engine.perf.PerfTrace
import audio.tembra.ui.navigation.Destination

/**
 * Routes every [ShortcutAction] to its scope's target (feature 011, contracts/keyboard-shortcuts.md): the Compose key
 * handlers, the iPad's menu commands and nothing else call it. Exhaustive, with no `else`: an action added to the enum
 * must be routed here. Returns true when a target acted.
 */
object ShortcutDispatcher {
    fun dispatch(action: ShortcutAction, targets: ShortcutTargets): Boolean = when (action) {
        ShortcutAction.OpenTimeline -> open(targets, Destination.Timeline)
        ShortcutAction.OpenTuner -> open(targets, Destination.Tuner)
        ShortcutAction.OpenMetronome -> open(targets, Destination.Metronome)
        ShortcutAction.OpenProjects -> open(targets, Destination.Projects)
        ShortcutAction.OpenProfile -> open(targets, Destination.Profile)
        ShortcutAction.ShowShortcuts -> targets.shell?.showShortcuts()
        // The newest overlay (rule 4). A focused Android pop-up or dialog takes Escape before the shell does; the iPad's
        // menu bar runs this for Escape, which UIKit keeps from Compose. With none open, Escape goes on (Android: back).
        ShortcutAction.CloseOverlay -> targets.shell?.closeOverlay()
        ShortcutAction.TimelinePlayPause -> targets.timeline?.playPause()
        ShortcutAction.TimelineSkipToStart -> targets.timeline?.skipToStart()
        ShortcutAction.TimelineStepBack -> targets.timeline?.step(direction = -1, bar = false)
        ShortcutAction.TimelineStepForward -> targets.timeline?.step(direction = 1, bar = false)
        ShortcutAction.TimelineBarBack -> targets.timeline?.step(direction = -1, bar = true)
        ShortcutAction.TimelineBarForward -> targets.timeline?.step(direction = 1, bar = true)
        ShortcutAction.TimelineSplit -> targets.timeline?.split()
        ShortcutAction.TimelineDeleteClip -> targets.timeline?.deleteClip()
        ShortcutAction.TimelineUndo -> targets.timeline?.undo()
        ShortcutAction.TimelineRedo -> targets.timeline?.redo()
        ShortcutAction.TimelineZoomIn -> targets.timeline?.zoom(1)
        ShortcutAction.TimelineZoomOut -> targets.timeline?.zoom(-1)
        ShortcutAction.TimelineMute -> targets.timeline?.toggleMute()
        ShortcutAction.TimelineSolo -> targets.timeline?.toggleSolo()
        ShortcutAction.MetronomeStartStop -> targets.metronome?.startStop()
        ShortcutAction.MetronomeTempoUp -> targets.metronome?.changeTempo(1)
        ShortcutAction.MetronomeTempoDown -> targets.metronome?.changeTempo(-1)
        ShortcutAction.MetronomeTempoUpTen -> targets.metronome?.changeTempo(10)
        ShortcutAction.MetronomeTempoDownTen -> targets.metronome?.changeTempo(-10)
        ShortcutAction.MetronomeTapTempo -> targets.metronome?.tapTempo()
        ShortcutAction.TunerAutoListen -> targets.tuner?.autoListen()
        ShortcutAction.TunerByEar -> targets.tuner?.byEar()
        ShortcutAction.TunerPlayString1 -> targets.tuner?.playString(1)
        ShortcutAction.TunerPlayString2 -> targets.tuner?.playString(2)
        ShortcutAction.TunerPlayString3 -> targets.tuner?.playString(3)
        ShortcutAction.TunerPlayString4 -> targets.tuner?.playString(4)
        ShortcutAction.TunerPlayString5 -> targets.tuner?.playString(5)
        ShortcutAction.TunerPlayString6 -> targets.tuner?.playString(6)
        ShortcutAction.TunerPlayString7 -> targets.tuner?.playString(7)
        ShortcutAction.TunerStopTone -> targets.tuner?.stopTone()
        ShortcutAction.TunerToggleLoop -> targets.tuner?.toggleLoop()
        ShortcutAction.ProjectsFocusSearch -> targets.projects?.focusSearch()
        ShortcutAction.ProjectsNewProject -> targets.projects?.newProject()
    } ?: false

    /** A destination chord: traced from the key to the navigation (the destination's first frame follows it). */
    private fun open(targets: ShortcutTargets, destination: Destination): Boolean? =
        PerfTrace.section(PerfSection.SHELL_SHORTCUT_NAVIGATE) { targets.shell?.open(destination) }
}
