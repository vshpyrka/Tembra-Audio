package audio.tembra.ui.platform

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** The platform's [TrackpadPinchSource], provided at the app's root from the graph; none in previews and tests. */
val LocalTrackpadPinchSource = staticCompositionLocalOf<TrackpadPinchSource> { NoTrackpadPinches }

/** A platform whose trackpad pinches Compose already delivers (Android), and previews. */
object NoTrackpadPinches : TrackpadPinchSource {
    override val pinches: Flow<TrackpadPinch> = emptyFlow()
}
