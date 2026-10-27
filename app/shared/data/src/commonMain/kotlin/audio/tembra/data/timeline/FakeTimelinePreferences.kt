package audio.tembra.data.timeline

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory preferences for tests, beside the interface; [overviewStripShown] starts at [initialShown]. */
class FakeTimelinePreferences(initialShown: Boolean = true) : TimelinePreferences {
    private val state = MutableStateFlow(initialShown)
    override val overviewStripShown: Flow<Boolean> = state

    /** What the last write left behind, for assertions. */
    val stored: Boolean get() = state.value

    override suspend fun setOverviewStripShown(shown: Boolean) {
        state.value = shown
    }
}
