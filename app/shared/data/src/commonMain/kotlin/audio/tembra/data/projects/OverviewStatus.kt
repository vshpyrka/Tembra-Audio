package audio.tembra.data.projects

/** Where an asset's waveform overview stands (spec FR-029): building with a 0..1 fraction, ready, or failed. */
sealed interface OverviewStatus {
    data class Building(val completedFraction: Float) : OverviewStatus

    data object Ready : OverviewStatus

    data object Failed : OverviewStatus
}
