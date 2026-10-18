package audio.tembra.core.timeline

/** The playhead marker in the ruler's bar row: its centre x, half width and vertical extent (both inclusive), in device pixels. */
data class MarkerGeometry(val x: Double, val halfWidth: Double, val top: Double, val bottom: Double)
