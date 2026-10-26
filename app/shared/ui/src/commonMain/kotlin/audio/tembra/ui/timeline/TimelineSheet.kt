package audio.tembra.ui.timeline

/**
 * The one overlay the timeline may show at a time (data-model.md §5): the tempo dialog, the metronome settings sheet
 * (which holds the time signature since D1, spec FR-037) or the snap sheet ([GridMenu], spec FR-018).
 */
enum class TimelineSheet { TempoDialog, MetronomeSettings, GridMenu }
