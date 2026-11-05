package audio.tembra.ui.mixer

import androidx.compose.runtime.Immutable
import audio.tembra.ui.designsystem.FaderScale

/** The master strip's state (feature 012, data-model.md §4): the project's master volume in whole percent. */
@Immutable
data class MasterUi(val percent: Int = DEFAULT_PERCENT) {
    companion object {
        /** Feature 009's default master volume (its spec FR-007), the master fader's home on a double tap. */
        const val DEFAULT_PERCENT = FaderScale.Percent.HOME_PERCENT
    }
}
