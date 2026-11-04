package audio.tembra.ui.timeline

/** The Timeline menu's full-screen row (feature 011, spec FR-010a): absent, "Full screen", or "Exit full screen". */
enum class FullScreenItem {
    /** The platform offers no app-requested full screen here (iPad, a phone, a window outside desktop windowing). */
    Hidden,
    Enter,
    Exit,
}
