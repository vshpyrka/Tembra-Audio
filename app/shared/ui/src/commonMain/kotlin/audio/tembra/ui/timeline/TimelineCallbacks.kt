package audio.tembra.ui.timeline

import audio.tembra.core.metronome.ClickSound
import audio.tembra.core.metronome.MetronomeStyle
import audio.tembra.core.metronome.Subdivision
import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.core.project.Beats
import audio.tembra.core.project.ClipId
import audio.tembra.core.project.CountIn
import audio.tembra.core.project.GainDb
import audio.tembra.core.project.GridSize
import audio.tembra.core.project.TrackId
import audio.tembra.ui.drop.DropOutcome

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
    /** The "+" and "−" keys (spec FR-015): one step is a factor of √2. */
    val onZoomStep: (steps: Int) -> Unit,
    val onScrollBy: (px: Double) -> Unit,
    /** The overview strip (spec FR-015, D1): the beat for the left edge, and a span to fill the lanes with. */
    val onScrollTo: (beats: Beats) -> Unit,
    val onZoomTo: (startBeats: Beats, endBeats: Beats) -> Unit,
    /** The overflow menu's "Overview strip" entry: show or hide the strip, a device preference (spec FR-015; T151). */
    val onOverviewStripShown: (shown: Boolean) -> Unit,
    /** Feature 011 (spec FR-010a): true asks for full screen, false for the window back. */
    val onFullScreen: (Boolean) -> Unit,
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
    val onClipDragStart: (ClipId) -> Unit,
    val onClipDrag: (ClipId, deltaBeats: Double, deltaTracks: Int) -> Unit,
    val onClipDragEnd: () -> Unit,
    val onClipDragCancel: () -> Unit,
    val onUndo: () -> Unit,
    val onRedo: () -> Unit,
    // Tempo and signature (US5)
    val onOpenSheet: (TimelineSheet?) -> Unit,
    val onTempoDragging: (Tempo) -> Unit,
    val onTempoCommit: (Tempo) -> Unit,
    val onTapTempo: (nanos: Long) -> Unit,
    val onTimeSignature: (TimeSignature) -> Unit,
    // Metronome settings sheet (US5)
    val onClickSound: (ClickSound) -> Unit,
    val onImportCustomSound: () -> Unit,
    val onSubdivision: (Subdivision) -> Unit,
    val onCycleBeat: (Int) -> Unit,
    val onStyle: (MetronomeStyle) -> Unit,
    // Recording (feature 010, contracts/timeline-recording-ui.md)
    /** The arm toggle: arms [TrackId], disarms it when it is the armed one. */
    val onArm: (TrackId) -> Unit = {},
    /** Record: starts a take (arming the selected or a new track first), ends the running one, cancels a count-in. */
    val onRecord: () -> Unit = {},
    /** The meter's clip square tapped: clears the latch. */
    val onResetClip: (TrackId) -> Unit = {},
    val onMonitorToggle: () -> Unit = {},
    val onCountIn: (CountIn) -> Unit = {},
    /** The denied snackbar's action: the system settings, or the prompt again. */
    val onRequestMicrophone: () -> Unit = {},
    // Files dropped on the tracks or the space below (feature 011, spec FR-026)
    val onDrop: (DropOutcome) -> Unit = {},
) {
    companion object {
        val None = TimelineCallbacks(
            onPlayPause = {}, onSkipToStart = {}, onStepBar = {}, onSeek = {}, onScrubStart = {}, onScrub = {}, onScrubEnd = {},
            onMasterVolume = {}, onMetronomeToggle = {}, onMetronomeSettings = {},
            onViewportMeasured = { _, _ -> }, onZoomAround = { _, _ -> }, onZoomStep = {}, onScrollBy = {},
            onScrollTo = {}, onZoomTo = { _, _ -> }, onOverviewStripShown = {}, onFullScreen = {}, onGrid = {},
            onAddTrack = {}, onSelectTrack = {}, onToggleMute = {}, onToggleSolo = {}, onGain = { _, _ -> }, onTrackSettings = {},
            onMoveTrack = { _, _ -> }, onDeleteTrack = {}, onConfirmDeleteTrack = {}, onDismissDeleteTrack = {}, onAddAudio = {}, onAddAudioAsNewTrack = {}, onExpandTrack = {},
            onSelectClip = { _, _ -> }, onClearSelection = {}, onSplit = {}, onMoveClip = { _, _, _ -> }, onDeleteClip = {}, onClipDragStart = {}, onClipDrag = { _, _, _ -> }, onClipDragEnd = {}, onClipDragCancel = {}, onUndo = {}, onRedo = {},
            onOpenSheet = {}, onTempoDragging = {}, onTempoCommit = {}, onTapTempo = {}, onTimeSignature = {},
            onClickSound = {}, onImportCustomSound = {}, onSubdivision = {}, onCycleBeat = {}, onStyle = {},
            onArm = {}, onRecord = {}, onResetClip = {}, onMonitorToggle = {}, onCountIn = {}, onRequestMicrophone = {},
        )
    }
}
