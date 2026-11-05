package audio.tembra.ui.session

import audio.tembra.core.project.Beats
import audio.tembra.core.project.IdGenerator
import audio.tembra.core.project.ProjectId
import audio.tembra.core.project.RecordTake
import audio.tembra.core.project.TrackId
import audio.tembra.core.recording.MonitorState
import audio.tembra.core.recording.RecordingEffect
import audio.tembra.core.recording.RecordingEvent
import audio.tembra.core.recording.RecordingPolicy
import audio.tembra.core.recording.RecordingState
import audio.tembra.core.recording.TakeStopReason
import audio.tembra.data.projects.AdoptedTake
import audio.tembra.data.projects.LiveOverview
import audio.tembra.data.projects.ProjectRepository
import audio.tembra.data.projects.TakeFreeSpace
import audio.tembra.data.projects.TakeManifest
import audio.tembra.data.timeline.TimelinePreferences
import audio.tembra.engine.ArmResult
import audio.tembra.engine.CaptureSnapshot
import audio.tembra.engine.PeakBuckets
import audio.tembra.engine.RecordConfig
import audio.tembra.engine.RecordStartResult
import audio.tembra.engine.TakeInfo
import audio.tembra.engine.TimelineAudio
import audio.tembra.engine.TransportSnapshot
import audio.tembra.engine.perf.PerfSection
import audio.tembra.engine.perf.PerfTrace
import audio.tembra.ui.platform.AudioRouteMonitor
import audio.tembra.ui.platform.BackgroundPlaybackHost
import audio.tembra.ui.platform.MicrophonePermission
import audio.tembra.ui.platform.MicrophonePermissionStatus
import audio.tembra.ui.platform.PlaybackNotification
import audio.tembra.ui.transport.TransportCopy
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The recording session beside the project session (feature 010, contracts/recording-session.md; research R15):
 * owns the armed track, the monitor state, the recording phase, the permission flow, the route observation, the
 * staging and adoption of a take, and the growing take's overview, and publishes them as [state]. Every transition
 * is a row of [RecordingPolicy]; this class runs the effects against the engine seam, the permission, the routes,
 * the background host, the device preference and the repository, and hands the finished take back to the session
 * through [RecordingHost].
 *
 * `kotlin-concurrency-and-flow`: [state] is renderable truth with a synchronous value; [inputTelemetry] is a hot,
 * replay-free per-frame read; every launch sits on the session scope under a name: `observeRoutes`,
 * `observePermission`, `observeCapture` (the 10 Hz mirror and the live peaks), `armJob`, `closeInput`, `startTake`,
 * `finaliseTake`, `requestPermission`, `rememberMonitor`. Nothing launches from `init`; [start] does, once.
 */
@Inject
@SingleIn(AppScope::class)
@OptIn(ExperimentalUuidApi::class)
class RecordingController(
    private val audio: TimelineAudio,
    private val permission: MicrophonePermission,
    private val routes: AudioRouteMonitor,
    private val host: BackgroundPlaybackHost,
    private val preferences: TimelinePreferences,
    private val repository: ProjectRepository,
    @Named("session") private val scope: CoroutineScope,
    /** Optional bindings: the graph provides neither; tests pass a fixed clock and a fixed token source. */
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val tokens: () -> String = { Uuid.random().toString() },
    private val mirrorIntervalMs: Long = MIRROR_INTERVAL_MS,
) {
    private val _state = MutableStateFlow(RecordingUi())
    val state: StateFlow<RecordingUi> = _state.asStateFlow()

    /** Draw-phase handle: at most one capture read per frame, memoised on the frame time (research R8). */
    val inputTelemetry: InputTelemetry = InputTelemetry(::readFrame)

    private var started = false
    private var sessionHost: RecordingHost? = null
    private var phase: RecordingState = RecordingState.Disarmed
    private var monitor: MonitorState = MonitorState.Off
    private var monitorRemembered = false
    private var disarmPending = false
    private var pendingArm: TrackId? = null
    /** The count-in of a Record press that armed first (FR-008): the take starts once the input is open. */
    private var pendingRecord: Int? = null
    private var armJob: Job? = null
    private var finaliseJob: Job? = null
    private var take: PendingTake? = null
    private var liveOverview: LiveOverview? = null
    private var peakIndex = 0L
    private var lastInputId: String? = null
    private var takeStartedAtMillis = 0L

    private val freeSpace = TakeFreeSpace(audio)
    private val mirrorWanted = MutableStateFlow(false)
    private val frameSnapshot = CaptureSnapshot()
    private var lastFrameNanos = Long.MIN_VALUE
    private val mirror = CaptureSnapshot()
    private val mirrorTransport = TransportSnapshot()
    private val peaks = PeakBuckets()

    /** The staged take between record start and adoption. */
    private class PendingTake(val manifest: TakeManifest, val projectId: ProjectId, val wavPath: String, val trackId: TrackId)

    // --- Lifecycle ---------------------------------------------------------------------------------------------------

    /** Starts the observers once with the session as the host; later calls do nothing. */
    fun start(host: RecordingHost) {
        if (started) return
        started = true
        sessionHost = host
        scope.launch(CoroutineName("observeRoutes")) { observeRoutes() }
        scope.launch(CoroutineName("observePermission")) { observePermission() }
        scope.launch(CoroutineName("observeCapture")) { observeCapture() }
        scope.launch(CoroutineName("rememberMonitor")) {
            monitorRemembered = preferences.monitorInput.first()
            publish()
        }
    }

    /** `ON_RESUME`: a grant made in the settings is noticed before the next arm. */
    fun refreshPermission() = permission.refresh()

    // --- Intents -----------------------------------------------------------------------------------------------------

    fun arm(trackId: TrackId) {
        // A device with no input at all (feature 011, FR-002): say so, ask for nothing, open nothing.
        if (permission.status.value == MicrophonePermissionStatus.Unavailable) {
            pendingRecord = null
            sessionHost?.notify(SessionMessage.NoMicrophone)
            return
        }
        pendingArm = trackId
        dispatch(RecordingEvent.Arm(trackId, permissionStatus()))
    }

    fun disarm() = dispatch(RecordingEvent.Disarm)

    /**
     * Record pressed: on the armed track the take is staged and started with [countInBeats] (ignored while playing),
     * refused with a message below 200 MB of free space (FR-025); while nothing is armed [armFirst] is armed and the
     * take starts once its input is open (FR-008); during a count-in it cancels, during a take it ends it (FR-009).
     */
    fun record(countInBeats: Int, armFirst: TrackId? = null) {
        val host = sessionHost ?: return
        val project = host.project ?: return
        when (phase) {
            RecordingState.Disarmed -> if (armFirst != null) {
                pendingRecord = countInBeats
                arm(armFirst)
            }
            is RecordingState.Arming -> pendingRecord = countInBeats
            is RecordingState.Armed -> if (freeSpace.canRecord(repository.audioDirectory(project.id))) {
                dispatch(RecordingEvent.Record(tokens(), countInBeats, host.isPlaying))
            } else {
                host.notify(SessionMessage.RecordRefused)
            }
            is RecordingState.PreRoll, is RecordingState.Recording, is RecordingState.Finalising ->
                dispatch(RecordingEvent.Record(tokens(), countInBeats, host.isPlaying))
        }
    }

    fun stop() = dispatch(RecordingEvent.Stop)

    fun cancel() = dispatch(RecordingEvent.Cancel)

    fun toggleMonitor() {
        PerfTrace.section(PerfSection.TIMELINE_MONITOR_TOGGLE) { dispatch(RecordingEvent.MonitorToggle(routes.route.value.outputIsSpeaker)) }
    }

    fun resetClip() = audio.resetClip()

    /** The "Open settings" action of the denied message, or the prompt again where the platform still shows it. */
    fun requestMicrophone() {
        when (permission.status.value) {
            MicrophonePermissionStatus.DeniedOpenSettings -> permission.openSystemSettings()
            MicrophonePermissionStatus.NotDetermined, MicrophonePermissionStatus.DeniedCanAsk, MicrophonePermissionStatus.Granted ->
                scope.launch(CoroutineName("requestPermission")) { permission.request() }
            // Nothing to allow until a microphone is connected.
            MicrophonePermissionStatus.Unavailable -> Unit
        }
    }

    // --- Events from the session -------------------------------------------------------------------------------------

    fun trackDeleted(trackId: TrackId) = dispatch(RecordingEvent.TrackDeleted(trackId))

    fun projectClosing() = dispatch(RecordingEvent.ProjectClosing)

    fun hostHidden() = dispatch(RecordingEvent.HostHidden)

    fun transportClosed() = dispatch(RecordingEvent.TransportClosed)

    fun interrupted() = dispatch(RecordingEvent.Interrupted)

    fun inputRouteChanged() = dispatch(RecordingEvent.InputChanged)

    fun outputRouteChanged(isSpeaker: Boolean) = dispatch(RecordingEvent.OutputChanged(isSpeaker))

    /** Waits until the running take (or count-in) has ended and been adopted; returns at once when none runs. */
    suspend fun awaitTakeEnded() {
        _state.first { !it.takeRunning }
    }

    // --- The policy --------------------------------------------------------------------------------------------------

    private fun dispatch(event: RecordingEvent) {
        val transition = RecordingPolicy.onEvent(phase, monitor, event, monitorRemembered, routes.route.value.outputIsSpeaker, disarmPending)
        val before = phase
        phase = transition.state
        monitor = transition.monitor
        // The disarm a leaving reason asked for survives until the take is finalised, and no longer.
        disarmPending = transition.disarmAfterFinalise || (disarmPending && transition.state is RecordingState.Finalising)
        if (transition.state is RecordingState.Disarmed) {
            liveOverview = null
            take = null
        }
        if (before is RecordingState.PreRoll && transition.state is RecordingState.Recording && event is RecordingEvent.TakeStarted) {
            sessionHost?.playbackStartedByTake()
        }
        publish()
        for (effect in transition.effects) run(effect, transition.state)
        settlePendingRecord(before, transition.state)
    }

    /** A Record press that armed first (FR-008) starts its take once the input is open, and is forgotten when the arm fails or is undone. */
    private fun settlePendingRecord(before: RecordingState, now: RecordingState) {
        val countIn = pendingRecord ?: return
        if (before is RecordingState.Arming && now is RecordingState.Armed) {
            pendingRecord = null
            record(countIn)
        } else if (now is RecordingState.Disarmed && before !is RecordingState.Disarmed) {
            pendingRecord = null
        }
    }

    private fun run(effect: RecordingEffect, state: RecordingState) {
        when (effect) {
            RecordingEffect.OpenInput -> openInput()
            RecordingEffect.CloseInput -> {
                closeInput()
                liveOverview = null
                publish()
            }
            is RecordingEffect.StartTake -> startTake(state, effect.countInBeats)
            is RecordingEffect.StopTake -> finaliseTake(effect.reason)
            RecordingEffect.CancelTake -> cancelTake()
            is RecordingEffect.SetMonitor -> setMonitor(effect.enabled)
            is RecordingEffect.RememberMonitor -> {
                monitorRemembered = effect.enabled
                scope.launch(CoroutineName("rememberMonitor")) { preferences.setMonitorInput(effect.enabled) }
            }
            RecordingEffect.RequestPermission -> requestPermissionThenArm()
            RecordingEffect.ShowDenied -> {
                pendingRecord = null
                sessionHost?.notify(SessionMessage.MicrophoneDenied(openSettings = permission.status.value == MicrophonePermissionStatus.DeniedOpenSettings))
            }
            RecordingEffect.ShowMonitorNeedsHeadphones -> sessionHost?.notify(SessionMessage.MonitorNeedsHeadphones)
            is RecordingEffect.Notify -> sessionHost?.notify(SessionMessage.TakeEnded(effect.reason))
            is RecordingEffect.KeepAlive -> keepAlive(effect.recording)
        }
    }

    private fun permissionStatus(): RecordingEvent.PermissionStatus = when (permission.status.value) {
        MicrophonePermissionStatus.Granted -> RecordingEvent.PermissionStatus.Granted
        MicrophonePermissionStatus.NotDetermined -> RecordingEvent.PermissionStatus.NotDetermined
        MicrophonePermissionStatus.DeniedCanAsk -> RecordingEvent.PermissionStatus.DeniedCanAsk
        MicrophonePermissionStatus.DeniedOpenSettings -> RecordingEvent.PermissionStatus.DeniedOpenSettings
        // Never reached: [arm] stops first on a device without a microphone; the policy then treats it as refused.
        MicrophonePermissionStatus.Unavailable -> RecordingEvent.PermissionStatus.DeniedCanAsk
    }

    /**
     * The input closes on the session scope (never cancelled), not inline: on iOS the close restarts the unit and
     * switches the session, work the engine runs on its serial blocking dispatcher after any arm launched before it
     * (T100). An arm launched after it queues behind it there, so the policy's close-then-open order holds.
     */
    private fun closeInput() {
        scope.launch(CoroutineName("closeInput")) { audio.disarm() }
    }

    private fun openInput() {
        armJob?.cancel()
        armJob = scope.launch(CoroutineName("armJob")) {
            PerfTrace.section(PerfSection.TIMELINE_ARM) {
                when (val result = audio.arm(MAX_INPUT_CHANNELS)) {
                    is ArmResult.Armed -> {
                        _state.value = _state.value.copy(inputChannels = result.channels)
                        dispatch(RecordingEvent.InputOpened(result.channels, result.sampleRate))
                    }
                    ArmResult.InputUnavailable, ArmResult.NotTransport -> dispatch(RecordingEvent.InputFailed)
                }
            }
        }
    }

    private fun requestPermissionThenArm() {
        val trackId = pendingArm ?: return
        scope.launch(CoroutineName("requestPermission")) {
            val status = permission.request()
            when (status) {
                MicrophonePermissionStatus.Granted -> dispatch(RecordingEvent.Arm(trackId, RecordingEvent.PermissionStatus.Granted))
                MicrophonePermissionStatus.NotDetermined, MicrophonePermissionStatus.DeniedCanAsk -> {
                    pendingRecord = null
                    sessionHost?.notify(SessionMessage.MicrophoneDenied(openSettings = false))
                }
                MicrophonePermissionStatus.DeniedOpenSettings -> {
                    pendingRecord = null
                    sessionHost?.notify(SessionMessage.MicrophoneDenied(openSettings = true))
                }
                MicrophonePermissionStatus.Unavailable -> {
                    pendingRecord = null
                    sessionHost?.notify(SessionMessage.NoMicrophone)
                }
            }
            publish()
        }
    }

    private fun startTake(state: RecordingState, countInBeats: Int) {
        val host = sessionHost ?: return
        val project = host.project ?: return
        val trackId = state.armedTrack ?: return
        val takeId = when (state) {
            is RecordingState.PreRoll -> state.takeId
            is RecordingState.Recording -> state.takeId
            RecordingState.Disarmed, is RecordingState.Arming, is RecordingState.Armed, is RecordingState.Finalising -> return
        }
        val track = project.tracks.firstOrNull { it.id == trackId } ?: return
        val trackIndex = project.tracks.indexOf(track)
        audio.readCapture(mirror)
        val manifest = TakeManifest(
            token = takeId,
            trackId = trackId,
            startBeats = host.positionBeats(),
            takeName = track.nextTakeName(),
            startedAt = clock(),
            alignmentFrames = mirror.alignmentFrames,
            sampleRate = mirror.sampleRate,
            channels = mirror.inputChannels,
        )
        takeStartedAtMillis = manifest.startedAt
        scope.launch(CoroutineName("startTake")) {
            val wavPath = repository.stageTake(project.id, manifest)
            take = PendingTake(manifest, project.id, wavPath, trackId)
            liveOverview = LiveOverview(channels = manifest.channels.coerceAtLeast(1))
            peakIndex = 0L
            val started = PerfTrace.section(PerfSection.TRANSPORT_RECORD_START) {
                audio.recordStart(RecordConfig.of(wavPath, trackIndex, countInBeats, manifest.sampleRate))
            }
            when (started) {
                RecordStartResult.Started -> if (countInBeats == 0) {
                    dispatch(RecordingEvent.TakeStarted(manifest.startBeats, manifest.startedAt))
                    host.playbackStartedByTake()
                } else {
                    // The pre-roll as one animation interval (contracts/perf-instrumentation.md): from the start to the first
                    // captured frame or the cancel. Launched outside the section above: the interval suspends, and on
                    // Android it is an async section so no thread-local section is left open across the wait.
                    scope.launch(CoroutineName("countInSpan")) {
                        PerfTrace.animation(PerfSection.TRANSPORT_COUNT_IN) { _state.first { it.phase !is RecordingState.PreRoll } }
                    }
                }
                RecordStartResult.NotArmed, RecordStartResult.TakeRunning, RecordStartResult.FileFailed -> dispatch(RecordingEvent.TakeEnded(TakeStopReason.WriteFailed))
            }
            publish()
        }
    }

    private fun cancelTake() {
        val pending = take
        audio.recordCancel()
        take = null
        liveOverview = null
        if (pending != null) scope.launch(CoroutineName("finaliseTake")) { repository.discardTake(pending.projectId, pending.manifest.token) }
        sessionHost?.playbackStoppedByCancel()
        publish()
    }

    private fun finaliseTake(reason: TakeStopReason) {
        val pending = take
        take = null
        finaliseJob = scope.launch(CoroutineName("finaliseTake")) {
            PerfTrace.section(PerfSection.TRANSPORT_RECORD_STOP) {
                val info = audio.recordStop()
                if (pending != null) adopt(pending, info.withReason(reason))
            }
            liveOverview = null
            dispatch(RecordingEvent.TakeFinalised)
        }
    }

    private fun TakeInfo.withReason(reason: TakeStopReason): TakeInfo =
        if (stopReason == TakeStopReason.User && reason != TakeStopReason.User) copy(stopReason = reason) else this

    private suspend fun adopt(pending: PendingTake, info: TakeInfo) {
        val host = sessionHost ?: return
        when (val adopted = repository.adoptTake(pending.projectId, pending.manifest, info)) {
            is AdoptedTake.Adopted -> {
                val startBeats = if (info.frames > 0 && info.startFrames >= 0 && info.sampleRate > 0) host.framesToBeats(info.startFrames, info.sampleRate) else adopted.startBeats
                host.applyTake(
                    RecordTake(
                        trackId = pending.trackId,
                        assetId = adopted.assetId,
                        asset = adopted.asset,
                        clipId = IdGenerator.clipId(),
                        clip = adopted.clip,
                        startBeats = startBeats,
                    ),
                    adopted.audioPath,
                    adopted.overviewPath,
                )
            }
            AdoptedTake.Discarded -> Unit
            is AdoptedTake.Failed -> host.notify(SessionMessage.TakeEnded(TakeStopReason.WriteFailed))
        }
    }

    private fun setMonitor(enabled: Boolean) {
        val project = sessionHost?.project ?: return
        val trackId = phase.armedTrack ?: return
        val index = project.tracks.indexOfFirst { it.id == trackId }
        if (index < 0) return
        audio.setMonitor(enabled, index)
    }

    private fun keepAlive(recording: Boolean) {
        val host = sessionHost ?: return
        val name = host.project?.name ?: ""
        when {
            recording -> this.host.keepAlive(PlaybackNotification.recording(name))
            host.isPlaying -> this.host.keepAlive(PlaybackNotification(TransportCopy.playing, name))
            else -> this.host.release()
        }
    }

    // --- Observers ---------------------------------------------------------------------------------------------------

    private suspend fun observeRoutes() {
        routes.route.collect { route ->
            val previous = lastInputId
            lastInputId = route.inputId
            if (previous != null && previous != route.inputId) dispatch(RecordingEvent.InputChanged)
            dispatch(RecordingEvent.OutputChanged(route.outputIsSpeaker))
        }
    }

    private suspend fun observePermission() {
        permission.status.collect { status ->
            if (status.isDenied && phase.armedTrack != null) dispatch(RecordingEvent.PermissionRevoked)
            publish()
        }
    }

    /**
     * The 10 Hz mirror: the pre-roll's beats, the take's start and its end by the engine, and the live peaks. Idle (no
     * timer scheduled) while nothing is armed, so a test's virtual clock can run to idle.
     */
    private suspend fun observeCapture() {
        while (true) {
            mirrorWanted.first { it }
            while (mirrorWanted.value) {
                delay(mirrorIntervalMs)
                mirrorTick()
            }
        }
    }

    private fun mirrorTick() {
        if (phase.armedTrack == null) return
        audio.readCapture(mirror)
        audio.readTransport(mirrorTransport)
        when (val current = phase) {
            is RecordingState.PreRoll -> {
                if (mirror.recording) {
                    val startBeats = sessionHost?.framesToBeats(mirror.takeStartFrames, mirror.sampleRate) ?: Beats.Zero
                    dispatch(RecordingEvent.TakeStarted(startBeats, clock()))
                } else if (mirrorTransport.countInBeatsLeft > 0 && mirrorTransport.countInBeatsLeft != current.beatsLeft) {
                    dispatch(RecordingEvent.PreRollTick(mirrorTransport.countInBeatsLeft))
                }
            }
            is RecordingState.Recording -> {
                if (!mirror.recording && mirror.stopReason != TakeStopReason.User) {
                    dispatch(RecordingEvent.TakeEnded(mirror.stopReason))
                } else {
                    pollPeaks()
                }
            }
            RecordingState.Disarmed, is RecordingState.Arming, is RecordingState.Armed, is RecordingState.Finalising -> Unit
        }
    }

    private fun pollPeaks() {
        val overview = liveOverview ?: return
        peakIndex = audio.readPeaks(peakIndex, peaks)
        if (peaks.count > 0) overview.append(peaks)
        publish()
    }

    private fun readFrame(frameNanos: Long, into: CaptureSnapshot) {
        if (frameNanos != lastFrameNanos) {
            lastFrameNanos = frameNanos
            audio.readCapture(frameSnapshot)
        }
        into.copyFrom(frameSnapshot)
    }

    private fun publish() {
        val pending = take
        val liveTake = if ((phase is RecordingState.Recording || phase is RecordingState.PreRoll) && pending != null) {
            LiveTakeUi(
                trackId = pending.trackId,
                clipName = pending.manifest.takeName,
                startBeats = (phase as? RecordingState.Recording)?.startBeats ?: pending.manifest.startBeats,
                overview = liveOverview?.asOverview(),
                framesCaptured = mirror.framesCaptured,
                sampleRate = pending.manifest.sampleRate,
            )
        } else {
            null
        }
        val permissionNow = permission.status.value
        mirrorWanted.value = phase.armedTrack != null
        _state.value = _state.value.copy(
            armedTrack = phase.armedTrack,
            phase = phase,
            monitor = monitor,
            inputChannels = if (phase.armedTrack == null) 0 else _state.value.inputChannels,
            liveTake = liveTake,
            permission = permissionNow,
        )
    }

    private companion object {
        /** The input's own channels up to two (FR-002). */
        const val MAX_INPUT_CHANNELS = 2
        const val MIRROR_INTERVAL_MS = 100L
    }
}
