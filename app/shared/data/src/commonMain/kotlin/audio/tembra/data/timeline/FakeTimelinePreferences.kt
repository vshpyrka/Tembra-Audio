package audio.tembra.data.timeline

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory preferences for tests, beside the interface; [overviewStripShown] starts at [initialShown], [monitorInput]
 * at [initialMonitorInput].
 */
class FakeTimelinePreferences(initialShown: Boolean = true, initialMonitorInput: Boolean = false) : TimelinePreferences {
    private val shown = MutableStateFlow(initialShown)
    private val monitoring = MutableStateFlow(initialMonitorInput)

    override val overviewStripShown: Flow<Boolean> = shown

    override val monitorInput: Flow<Boolean> = monitoring

    /** What the last strip write left behind, for assertions. */
    val stored: Boolean get() = shown.value

    /** What the last monitoring write left behind, for assertions. */
    val storedMonitorInput: Boolean get() = monitoring.value

    override suspend fun setOverviewStripShown(shown: Boolean) {
        this.shown.value = shown
    }

    override suspend fun setMonitorInput(enabled: Boolean) {
        monitoring.value = enabled
    }
}
