package audio.tembra.ui.platform

import androidx.compose.ui.geometry.Offset

/**
 * One step of a trackpad pinch the platform shell saw (feature 011, spec FR-014, research R9): [scale] is the change
 * since the previous step (above 1 spreads the fingers, zooming in) and [focus] the pinch's centre in the Compose root's
 * pixels.
 */
data class TrackpadPinch(val scale: Float, val focus: Offset)
