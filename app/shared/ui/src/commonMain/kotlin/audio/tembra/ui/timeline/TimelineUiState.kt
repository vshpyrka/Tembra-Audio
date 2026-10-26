package audio.tembra.ui.timeline

import androidx.compose.runtime.Immutable
import audio.tembra.core.metronome.MetronomeSettings
import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.core.project.Beats
import audio.tembra.core.project.GridSize
import audio.tembra.core.project.PlaybackState
import audio.tembra.core.project.TrackId
import audio.tembra.core.project.ZoomControl
import audio.tembra.core.timeline.TimelineViewport
import audio.tembra.ui.metronome.TapCaption
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
    /** The always-visible zoom control this device shows (spec FR-015, D1): a device preference, not a project's. */
    val zoomControl: ZoomControl = ZoomControl.Default,
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
) {
    val isEmpty: Boolean get() = loaded && tracks.isEmpty()

    val isPlaying: Boolean get() = playback is PlaybackState.Playing || playback is PlaybackState.Starting

    val transport: TransportUiState
        get() = TransportUiState(
            playback = playback,
            positionSeconds = positionSeconds,
            lengthSeconds = lengthSeconds,
            masterVolume = masterVolume,
            metronomeOn = metronomeOn,
            hasProject = loaded,
        )

    val selectedClip: ClipUi?
        get() = when (val current = selection) {
            Selection.None -> null
            is Selection.TrackSelected -> null
            is Selection.ClipSelected -> tracks.firstOrNull { it.id == current.trackId }?.clips?.firstOrNull { it.id == current.clipId }
        }
}
