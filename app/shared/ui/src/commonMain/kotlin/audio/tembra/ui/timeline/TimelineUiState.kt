package audio.tembra.ui.timeline

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import audio.tembra.core.metronome.MetronomeSettings
import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.core.project.Beats
import audio.tembra.core.project.CountIn
import audio.tembra.core.project.GridSize
import audio.tembra.core.project.PlaybackState
import audio.tembra.core.project.TrackId
import audio.tembra.core.recording.MonitorState
import audio.tembra.core.timeline.TimelineViewport
import audio.tembra.ui.metronome.TapCaption
import audio.tembra.ui.platform.MicrophonePermissionStatus
import audio.tembra.ui.session.LiveTakeUi
import audio.tembra.ui.transport.RecordButtonState
import audio.tembra.ui.transport.TransportUiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Everything the Timeline screen renders (data-model.md §5; Constitution II): the open project's display values,
 * the view state the ViewModel owns (selection, viewport, grid, following, sheet), the transport, and the flags the
 * toolbar needs. Immutable; the per-frame playhead and readout read the telemetry in draw instead.
 */
@Immutable
data class TimelineUiState(
    /** False until the session has opened a project (or reported that it cannot). */
    val loaded: Boolean = false,
    val projectName: String = "",
    val tempo: Tempo = Tempo.Default,
    val timeSignature: TimeSignature = TimeSignature.Default,
    val tracks: ImmutableList<TrackUi> = persistentListOf(),
    val lengthBeats: Beats = Beats.Zero,
    val lengthSeconds: Double = 0.0,
    val selection: Selection = Selection.None,
    val viewport: TimelineViewport = TimelineViewport(pixelsPerBeat = 1.0, scrollBeats = Beats.Zero, widthPx = 0.0, densityScale = 1.0),
    val grid: GridSize = GridSize.Default,
    /** The overview strip shows above the transport bar (spec FR-015; T151): a device preference, not a project's. */
    val overviewStripShown: Boolean = true,
    /** The menu's full-screen row (feature 011, spec FR-010a); follows the platform's window controls. */
    val fullScreenItem: FullScreenItem = FullScreenItem.Hidden,
    /** True while the view pages after the playhead (spec FR-012); a manual scroll suspends it. */
    val following: Boolean = true,
    val playback: PlaybackState = PlaybackState.Stopped(Beats.Zero),
    /** The 10 Hz mirror of the playhead for semantics and tests; the overlay reads the telemetry per frame. */
    val positionBeats: Beats = Beats.Zero,
    val positionSeconds: Double = 0.0,
    val metronomeOn: Boolean = false,
    val masterVolume: Int = 80,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    /** A clip is selected and the playhead, snapped to the grid, lies strictly inside it, more than 0.01 s from both edges (spec FR-032). */
    val canSplit: Boolean = false,
    /** The track whose settings are open inline on expanded windows (spec FR-041). */
    val expandedTrack: TrackId? = null,
    val sheet: TimelineSheet? = null,
    /** Every visible clip's overview is ready: the `screen.timeline` node reads "ready" (contracts/perf-instrumentation.md). */
    val isFirstContentReady: Boolean = false,
    /** Assets whose file is missing: their clips draw unavailable (spec FR-030). */
    val missingAssets: Int = 0,
    /** The track whose deletion awaits confirmation (spec FR-021); the content renders the dialog while set. */
    val confirmDelete: TrackId? = null,
    /** The clip move in progress, drawn by the lanes (spec FR-033). */
    val drag: ClipDragUi? = null,
    /** The global metronome's settings as the sheet edits them (spec FR-038). */
    val metronomeSettings: MetronomeSettings = MetronomeSettings(),
    /** The tap-tempo run's caption in the tempo dialog. */
    val tapCaption: TapCaption = TapCaption.Rest,
    // Recording (feature 010, data-model.md §6; contracts/recording-session.md "Availability").
    /** True while a count-in or a take runs: the structural edits below are unavailable (spec FR-018). */
    val takeRunning: Boolean = false,
    /** Record's state as the session sees it: [RecordButtonState.Idle], [RecordButtonState.CountIn] or [RecordButtonState.Recording], never hidden. */
    val recordPhase: RecordButtonState = RecordButtonState.Idle,
    /** Beats of a running count-in still to sound, down to 1; -1 when none runs. */
    val countInBeatsLeft: Int = -1,
    val monitor: MonitorState = MonitorState.Off,
    val countIn: CountIn = CountIn.ONE_BAR,
    /** The growing take, drawn above the armed track's clips (spec FR-010). */
    val liveTake: LiveTakeUi? = null,
    val permission: MicrophonePermissionStatus = MicrophonePermissionStatus.NotDetermined,
    /** The transport bar's width, which places Record and the monitor toggle (spec FR-007, FR-026); 0 until measured. */
    val barWidth: Dp = 0.dp,
) {
    val isEmpty: Boolean get() = loaded && tracks.isEmpty()

    val isPlaying: Boolean get() = playback is PlaybackState.Playing || playback is PlaybackState.Starting

    /** The armed track, if any. */
    val armedTrack: TrackId? get() = tracks.firstOrNull { it.armed }?.id

    /** Record sits in the bar from 432 dp (spec FR-007); narrower, in the armed track's compact header. */
    val recordInBar: Boolean get() = barWidth >= RecordInBarFrom

    /** What the bar shows: the session's phase, or hidden while the bar is too narrow for it. */
    val recordButton: RecordButtonState get() = if (recordInBar) recordPhase else RecordButtonState.Hidden

    /** The monitor toggle is in the toolbar from 600 dp, in the compact overflow below (contracts/timeline-recording-ui.md). */
    val monitorInToolbar: Boolean get() = barWidth >= MonitorInToolbarFrom

    // FR-018: what a running count-in or take makes unavailable.
    val canAddTrack: Boolean get() = loaded && !takeRunning
    val canImport: Boolean get() = loaded && !takeRunning
    val canEditTempo: Boolean get() = loaded && !takeRunning
    val canEditSignature: Boolean get() = loaded && !takeRunning
    val canEditCountIn: Boolean get() = loaded && !takeRunning
    val canArm: Boolean get() = loaded && !takeRunning
    val canMoveOrDeleteTrack: Boolean get() = loaded && !takeRunning

    val transport: TransportUiState
        get() = TransportUiState(
            playback = playback,
            positionSeconds = positionSeconds,
            lengthSeconds = lengthSeconds,
            masterVolume = masterVolume,
            metronomeOn = metronomeOn,
            hasProject = loaded,
            record = recordButton,
            countInBeatsLeft = countInBeatsLeft,
            seekEnabled = !takeRunning,
        )

    val selectedClip: ClipUi?
        get() = when (val current = selection) {
            Selection.None -> null
            is Selection.TrackSelected -> null
            is Selection.ClipSelected -> tracks.firstOrNull { it.id == current.trackId }?.clips?.firstOrNull { it.id == current.clipId }
        }
}

/** From this bar width Record is in the transport bar (spec FR-007); narrower bars keep feature 009's bar. */
internal val RecordInBarFrom: Dp = 432.dp

/** From this bar width the monitor toggle sits in the toolbar after the scissors; narrower, in the overflow menu. */
internal val MonitorInToolbarFrom: Dp = 600.dp
