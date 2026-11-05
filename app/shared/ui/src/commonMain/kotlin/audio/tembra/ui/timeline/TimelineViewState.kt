package audio.tembra.ui.timeline

import audio.tembra.core.metronome.TapTempoSequence
import audio.tembra.core.project.Beats
import audio.tembra.core.project.GridSize
import audio.tembra.core.project.ProjectId
import audio.tembra.core.project.TrackId
import audio.tembra.core.timeline.TimelineViewport
import audio.tembra.ui.metronome.TapCaption

/**
 * What the Timeline ViewModel owns beside the session (data-model.md §5): the viewport, the selection, the grid
 * override (null until the user changes it: the project's stored grid applies), following, the open sheet, the
 * expanded inline-settings track, the pending delete confirmation and the 10 Hz playhead mirror. Reset when another
 * project opens ([projectId]), so nothing global lives here: the device's zoom control and the metronome settings sit
 * beside it in the ViewModel.
 */
internal data class TimelineViewState(
    val projectId: ProjectId? = null,
    val viewport: TimelineViewport = TimelineViewport(pixelsPerBeat = 1.0, scrollBeats = Beats.Zero, widthPx = 0.0, densityScale = 1.0),
    val selection: Selection = Selection.None,
    val grid: GridSize? = null,
    val following: Boolean = true,
    val sheet: TimelineSheet? = null,
    val expandedTrack: TrackId? = null,
    val confirmDelete: TrackId? = null,
    val drag: ClipDragState? = null,
    val tap: TapTempoSequence = TapTempoSequence(),
    val tapCaption: TapCaption = TapCaption.Rest,
    val positionBeats: Beats = Beats.Zero,
    val positionSeconds: Double = 0.0,
    /** The mixer sheet (feature 012, spec FR-022): carried over when another project opens, unlike the rest. */
    val mixer: MixerViewState = MixerViewState(),
)
