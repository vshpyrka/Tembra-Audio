package audio.tembra.data.timeline

import audio.tembra.core.project.ZoomControl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory preferences for tests, beside the interface. */
class FakeTimelinePreferences(initial: ZoomControl = ZoomControl.Default) : TimelinePreferences {
    private val state = MutableStateFlow(initial)
    override val zoomControl: Flow<ZoomControl> = state

    /** What the last write left behind, for assertions. */
    val stored: ZoomControl get() = state.value

    override suspend fun setZoomControl(control: ZoomControl) {
        state.value = control
    }
}
