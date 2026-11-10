package audio.tembra.ui.designsystem

/** One frame's reading for a [LevelMeter] (feature 012): the linear peak per side and the clip latch, filled without allocation in the draw phase. */
internal class MeterSample {
    var left: Float = 0f
    var right: Float = 0f
    var clipped: Boolean = false
}
