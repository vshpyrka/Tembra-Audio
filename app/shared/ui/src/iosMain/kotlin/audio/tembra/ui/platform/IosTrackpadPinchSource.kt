package audio.tembra.ui.platform

import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The iPad's trackpad pinches from the shell's bridge (`TrackpadPinchBridge.swift`, feature 011, research R9): the
 * bridge calls [report] on the main thread for every change of a pinch made with a trackpad.
 */
class IosTrackpadPinchSource : TrackpadPinchSource {
    private val _pinches = MutableSharedFlow<TrackpadPinch>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val pinches: SharedFlow<TrackpadPinch> = _pinches.asSharedFlow()

    /** [scale] relative to the last report; ([x], [y]) the pinch's centre in the Compose view's pixels. */
    fun report(scale: Double, x: Double, y: Double) {
        _pinches.tryEmit(TrackpadPinch(scale.toFloat(), Offset(x.toFloat(), y.toFloat())))
    }
}
