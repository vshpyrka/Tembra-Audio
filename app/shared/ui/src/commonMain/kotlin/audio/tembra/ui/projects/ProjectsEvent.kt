package audio.tembra.ui.projects

/** One-shot events the library hands to its screen: exactly once, to the one collector. */
sealed interface ProjectsEvent {
    /** A project was opened in the session; the shell shows the Timeline. */
    data object OpenedInTimeline : ProjectsEvent
}
