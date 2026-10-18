package audio.tembra.ui.timeline

import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.core.project.Beats
import audio.tembra.core.project.ClipId
import audio.tembra.core.project.GainDb
import audio.tembra.core.project.GridSize
import audio.tembra.core.project.TrackId

/** The timeline's intents as plain functions (Constitution II); [None] for previews and tests. */
data class TimelineCallbacks(
    // Transport (US1)
    val onPlayPause: () -> Unit,
    val onSkipToStart: () -> Unit,
    val onStepBar: (delta: Int) -> Unit,
    val onSeek: (beats: Beats) -> Unit,
    val onScrubStart: () -> Unit,
    val onScrub: (beats: Beats) -> Unit,
    val onScrubEnd: (beats: Beats) -> Unit,
    val onMasterVolume: (percent: Int) -> Unit,
    val onMetronomeToggle: () -> Unit,
    val onMetronomeSettings: () -> Unit,
    // Viewport (US3)
    val onViewportMeasured: (widthPx: Double, densityScale: Double) -> Unit,
    val onZoomAround: (centreX: Double, factor: Double) -> Unit,
    val onZoomStep: (steps: Int) -> Unit,
    val onFitProject: () -> Unit,
    val onScrollBy: (px: Double) -> Unit,
    val onGrid: (GridSize) -> Unit,
    // Tracks (US2)
    val onAddTrack: () -> Unit,
    val onSelectTrack: (TrackId) -> Unit,
    val onToggleMute: (TrackId) -> Unit,
    val onToggleSolo: (TrackId) -> Unit,
    val onGain: (TrackId, GainDb) -> Unit,
    val onTrackSettings: (TrackId) -> Unit,
    val onMoveTrack: (TrackId, Int) -> Unit,
    val onDeleteTrack: (TrackId) -> Unit,
    val onConfirmDeleteTrack: () -> Unit,
    val onDismissDeleteTrack: () -> Unit,
    val onAddAudio: (TrackId?) -> Unit,
    val onAddAudioAsNewTrack: () -> Unit,
    val onExpandTrack: (TrackId?) -> Unit,
    // Clips (US4)
    val onSelectClip: (TrackId, ClipId) -> Unit,
    val onClearSelection: () -> Unit,
    val onSplit: () -> Unit,
    val onMoveClip: (ClipId, TrackId, Beats) -> Unit,
    val onDeleteClip: (ClipId) -> Unit,
    val onUndo: () -> Unit,
    val onRedo: () -> Unit,
    // Tempo and signature (US5)
    val onOpenSheet: (TimelineSheet?) -> Unit,
    val onTempoDragging: (Tempo) -> Unit,
    val onTempoCommit: (Tempo) -> Unit,
    val onTapTempo: (nanos: Long) -> Unit,
    val onTimeSignature: (TimeSignature) -> Unit,
) {
    companion object {
        val None = TimelineCallbacks(
            onPlayPause = {}, onSkipToStart = {}, onStepBar = {}, onSeek = {}, onScrubStart = {}, onScrub = {}, onScrubEnd = {},
            onMasterVolume = {}, onMetronomeToggle = {}, onMetronomeSettings = {},
            onViewportMeasured = { _, _ -> }, onZoomAround = { _, _ -> }, onZoomStep = {}, onFitProject = {}, onScrollBy = {}, onGrid = {},
            onAddTrack = {}, onSelectTrack = {}, onToggleMute = {}, onToggleSolo = {}, onGain = { _, _ -> }, onTrackSettings = {},
            onMoveTrack = { _, _ -> }, onDeleteTrack = {}, onConfirmDeleteTrack = {}, onDismissDeleteTrack = {}, onAddAudio = {}, onAddAudioAsNewTrack = {}, onExpandTrack = {},
            onSelectClip = { _, _ -> }, onClearSelection = {}, onSplit = {}, onMoveClip = { _, _, _ -> }, onDeleteClip = {}, onUndo = {}, onRedo = {},
            onOpenSheet = {}, onTempoDragging = {}, onTempoCommit = {}, onTapTempo = {}, onTimeSignature = {},
        )
    }
}
