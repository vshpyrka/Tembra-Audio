package audio.tembra.ui.session

import kotlinx.coroutines.flow.MutableStateFlow

/** A session that is never busy unless a test says so (feature 011's busy pointer). */
class FakeSessionActivity(busy: Boolean = false) : SessionActivity {
    override val busy = MutableStateFlow(busy)
}
