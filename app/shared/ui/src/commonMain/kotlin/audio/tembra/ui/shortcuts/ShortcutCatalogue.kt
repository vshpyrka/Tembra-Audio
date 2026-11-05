package audio.tembra.ui.shortcuts

import androidx.compose.ui.input.key.Key
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * The single source of every shortcut (feature 011, spec FR-017 to FR-023, FR-018a; contracts/keyboard-shortcuts.md):
 * the shell's and the destinations' key handlers, the shortcut sheet, Android's system list and the iPad's menu bar all
 * read it. Chords written with Ctrl are Command on iPad. The Timeline's entries are feature 009's keys (FR-057).
 */
object ShortcutCatalogue {
    private fun char(c: Char, primary: Boolean = false, shift: Boolean = false) = KeyChord(ChordKey.Character(c), primary, shift)
    private fun named(key: Key, primary: Boolean = false, shift: Boolean = false) = KeyChord(ChordKey.Named(key), primary, shift)
    private fun entry(scope: ShortcutScope, action: ShortcutAction, label: String, vararg chords: KeyChord) = Shortcut(chords.toList(), scope, action, label)

    val all: ImmutableList<Shortcut> = persistentListOf(
        entry(ShortcutScope.Everywhere, ShortcutAction.OpenTimeline, ShortcutCopy.openTimeline, char('1', primary = true)),
        entry(ShortcutScope.Everywhere, ShortcutAction.OpenTuner, ShortcutCopy.openTuner, char('2', primary = true)),
        entry(ShortcutScope.Everywhere, ShortcutAction.OpenMetronome, ShortcutCopy.openMetronome, char('3', primary = true)),
        entry(ShortcutScope.Everywhere, ShortcutAction.OpenProjects, ShortcutCopy.openProjects, char('4', primary = true)),
        entry(ShortcutScope.Everywhere, ShortcutAction.OpenProfile, ShortcutCopy.openProfile, char('5', primary = true)),
        entry(ShortcutScope.Everywhere, ShortcutAction.ShowShortcuts, ShortcutCopy.showShortcuts, char('/', primary = true)),
        // Escape, and Apple's cancel chord: on iPad UIKit keeps Escape from the app, so the menu bar serves ⌘. (research R6).
        entry(ShortcutScope.Everywhere, ShortcutAction.CloseOverlay, ShortcutCopy.closeOverlay, named(Key.Escape), char('.', primary = true)),

        entry(ShortcutScope.Timeline, ShortcutAction.TimelinePlayPause, ShortcutCopy.playPause, named(Key.Spacebar)),
        entry(ShortcutScope.Timeline, ShortcutAction.TimelineSkipToStart, ShortcutCopy.skipToStart, named(Key.MoveHome)),
        entry(ShortcutScope.Timeline, ShortcutAction.TimelineStepBack, ShortcutCopy.stepBack, named(Key.DirectionLeft)),
        entry(ShortcutScope.Timeline, ShortcutAction.TimelineStepForward, ShortcutCopy.stepForward, named(Key.DirectionRight)),
        entry(ShortcutScope.Timeline, ShortcutAction.TimelineBarBack, ShortcutCopy.barBack, named(Key.DirectionLeft, shift = true)),
        entry(ShortcutScope.Timeline, ShortcutAction.TimelineBarForward, ShortcutCopy.barForward, named(Key.DirectionRight, shift = true)),
        entry(ShortcutScope.Timeline, ShortcutAction.TimelineSplit, ShortcutCopy.split, char('s')),
        entry(ShortcutScope.Timeline, ShortcutAction.TimelineDeleteClip, ShortcutCopy.deleteClip, named(Key.Delete), named(Key.Backspace)),
        entry(ShortcutScope.Timeline, ShortcutAction.TimelineUndo, ShortcutCopy.undo, char('z', primary = true)),
        entry(ShortcutScope.Timeline, ShortcutAction.TimelineRedo, ShortcutCopy.redo, char('z', primary = true, shift = true), char('y', primary = true)),
        entry(ShortcutScope.Timeline, ShortcutAction.TimelineZoomIn, ShortcutCopy.zoomIn, char('+'), char('=')),
        entry(ShortcutScope.Timeline, ShortcutAction.TimelineZoomOut, ShortcutCopy.zoomOut, char('-')),
        entry(ShortcutScope.Timeline, ShortcutAction.TimelineMute, ShortcutCopy.mute, char('m')),
        entry(ShortcutScope.Timeline, ShortcutAction.TimelineSolo, ShortcutCopy.solo, char('o')),
        // Feature 010 (FR-028): Escape during a count-in is the timeline's own, beside the clip drag's (TimelineKeyHandling).
        entry(ShortcutScope.Timeline, ShortcutAction.TimelineArm, ShortcutCopy.arm, char('a')),
        entry(ShortcutScope.Timeline, ShortcutAction.TimelineRecord, ShortcutCopy.record, char('r')),
        entry(ShortcutScope.Timeline, ShortcutAction.TimelineMonitor, ShortcutCopy.monitor, char('i')),

        entry(ShortcutScope.Tuner, ShortcutAction.TunerAutoListen, ShortcutCopy.autoListen, char('a')),
        entry(ShortcutScope.Tuner, ShortcutAction.TunerByEar, ShortcutCopy.byEar, char('e')),
        entry(ShortcutScope.Tuner, ShortcutAction.TunerPlayString1, ShortcutCopy.playString(1), char('1')),
        entry(ShortcutScope.Tuner, ShortcutAction.TunerPlayString2, ShortcutCopy.playString(2), char('2')),
        entry(ShortcutScope.Tuner, ShortcutAction.TunerPlayString3, ShortcutCopy.playString(3), char('3')),
        entry(ShortcutScope.Tuner, ShortcutAction.TunerPlayString4, ShortcutCopy.playString(4), char('4')),
        entry(ShortcutScope.Tuner, ShortcutAction.TunerPlayString5, ShortcutCopy.playString(5), char('5')),
        entry(ShortcutScope.Tuner, ShortcutAction.TunerPlayString6, ShortcutCopy.playString(6), char('6')),
        entry(ShortcutScope.Tuner, ShortcutAction.TunerPlayString7, ShortcutCopy.playString(7), char('7')),
        entry(ShortcutScope.Tuner, ShortcutAction.TunerStopTone, ShortcutCopy.stopTone, named(Key.Spacebar)),
        entry(ShortcutScope.Tuner, ShortcutAction.TunerToggleLoop, ShortcutCopy.toggleLoop, char('l')),

        entry(ShortcutScope.Metronome, ShortcutAction.MetronomeStartStop, ShortcutCopy.startStop, named(Key.Spacebar)),
        entry(ShortcutScope.Metronome, ShortcutAction.MetronomeTempoUp, ShortcutCopy.tempoUp, named(Key.DirectionUp)),
        entry(ShortcutScope.Metronome, ShortcutAction.MetronomeTempoDown, ShortcutCopy.tempoDown, named(Key.DirectionDown)),
        entry(ShortcutScope.Metronome, ShortcutAction.MetronomeTempoUpTen, ShortcutCopy.tempoUpTen, named(Key.DirectionUp, shift = true)),
        entry(ShortcutScope.Metronome, ShortcutAction.MetronomeTempoDownTen, ShortcutCopy.tempoDownTen, named(Key.DirectionDown, shift = true)),
        entry(ShortcutScope.Metronome, ShortcutAction.MetronomeTapTempo, ShortcutCopy.tapTempo, char('t')),

        entry(ShortcutScope.Projects, ShortcutAction.ProjectsFocusSearch, ShortcutCopy.focusSearch, char('f', primary = true)),
        entry(ShortcutScope.Projects, ShortcutAction.ProjectsNewProject, ShortcutCopy.newProject, char('n', primary = true)),
    )

    /**
     * The action [input] stands for in [scope] (not Everywhere's), or null. Where a menu serves the primary chords
     * ([ShortcutPlatform.menuServesPrimaryChords]), each entry's [Shortcut.menuChord] is left to it, so none fires twice.
     */
    fun match(input: KeyInput, scope: ShortcutScope, platform: ShortcutPlatform): ShortcutAction? =
        all.firstOrNull { shortcut ->
            shortcut.scope == scope && shortcut.chords.any { chord ->
                chord.matches(input, platform.primary) && !(platform.menuServesPrimaryChords && chord == shortcut.menuChord)
            }
        }?.action
}
