package audio.tembra.core.timeline

/**
 * One ruler row at its chosen level: [step] is the labelled spacing (beats for the bar row, seconds for the time
 * row), [finerStep] the next denser level or null at the finest, [labels] the texts in view and [ticks] the x
 * positions of the level below the labelled one (spec FR-014). The grid is drawn at the bar row's [step].
 */
data class RulerRow(
    val step: Double,
    val finerStep: Double?,
    val labels: List<RulerLabel>,
    val ticks: List<Double>,
)
