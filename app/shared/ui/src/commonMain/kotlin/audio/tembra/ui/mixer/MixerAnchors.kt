package audio.tembra.ui.mixer

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import audio.tembra.ui.timeline.TimelineLayout
import kotlin.math.max

/**
 * The sheet's two heights for a layout and a lanes box (feature 012, data-model.md §5; spec FR-018 to FR-021): compact
 * and medium windows open at `max(256 dp, 45 %)` of the lanes box and fill it at [full]; landscape has the one anchor
 * ([singleAnchor], the full height); expanded windows leave two expanded lanes visible at the half height
 * (`lanes − 2 × 96 dp`, never under 256 dp). [half] never exceeds [full]; both are 0 before the lanes box is measured.
 */
@Immutable
data class MixerAnchors(
    val half: Dp,
    val full: Dp,
    val singleAnchor: Boolean = false,
) {
    /** The sheet height at [height]: landscape's one anchor is the full one whatever is asked. */
    fun heightOf(height: MixerHeight): Dp = if (singleAnchor || height == MixerHeight.Full) full else half

    companion object {
        /**
         * The least half height on every layout: the spec's 240 dp floor (FR-018, FR-021) plus the 16 dp by which the
         * handle row exceeds research R2's budget, so the compact strip's fader keeps a 120 dp throw (FR-018, FR-025).
         */
        val MinHalf: Dp = 256.dp

        /** The share of the lanes box the half sheet takes on compact and medium windows. */
        const val HALF_FRACTION = 0.45f

        /** Two expanded lanes stay visible above the half sheet on expanded windows (spec FR-021; research R2). */
        val ExpandedLanesKept: Dp = 192.dp

        val None = MixerAnchors(half = 0.dp, full = 0.dp)

        fun of(layout: TimelineLayout, lanesHeight: Dp): MixerAnchors {
            if (lanesHeight <= 0.dp) return None
            val full = lanesHeight
            return when (layout) {
                TimelineLayout.Compact, TimelineLayout.Medium -> MixerAnchors(half = atMost(max(MinHalf.value, full.value * HALF_FRACTION).dp, full), full = full)
                TimelineLayout.Landscape -> MixerAnchors(half = full, full = full, singleAnchor = true)
                TimelineLayout.Expanded -> MixerAnchors(half = atMost(max(MinHalf.value, (full - ExpandedLanesKept).value).dp, full), full = full)
            }
        }

        private fun atMost(value: Dp, limit: Dp): Dp = if (value > limit) limit else value
    }
}
