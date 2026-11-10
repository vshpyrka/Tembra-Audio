package audio.tembra.ui.shortcuts

/** The scopes that can act now (feature 011): null where the scope's destination is not showing; [mixer] is the focused strip (feature 012). */
data class ShortcutTargets(
    val shell: ShellShortcuts? = null,
    val timeline: TimelineShortcuts? = null,
    val tuner: TunerShortcuts? = null,
    val metronome: MetronomeShortcuts? = null,
    val projects: ProjectsShortcuts? = null,
    val mixer: MixerShortcuts? = null,
)
