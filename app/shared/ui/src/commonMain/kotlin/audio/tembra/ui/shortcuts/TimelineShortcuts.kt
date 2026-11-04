package audio.tembra.ui.shortcuts

/** What the Timeline's keys do (feature 009's FR-057, listed in feature 011's catalogue); each returns true when it acted. */
interface TimelineShortcuts {
    fun playPause(): Boolean

    fun skipToStart(): Boolean

    /** One grid step ([bar] false) or one bar, back ([direction] < 0) or forward. */
    fun step(direction: Int, bar: Boolean): Boolean

    fun split(): Boolean

    fun deleteClip(): Boolean

    fun undo(): Boolean

    fun redo(): Boolean

    fun zoom(steps: Int): Boolean

    fun toggleMute(): Boolean

    fun toggleSolo(): Boolean

    /** Feature 010 (FR-028): arm the selected track, record (or end the take, or cancel a count-in), toggle monitoring. */
    fun arm(): Boolean

    fun record(): Boolean

    fun toggleMonitor(): Boolean
}
