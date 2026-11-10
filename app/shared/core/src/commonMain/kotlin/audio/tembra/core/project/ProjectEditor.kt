package audio.tembra.core.project

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlin.math.max

/**
 * The pure editor (Constitution V; data-model.md section 1): `apply(project, edit)` returns the next immutable
 * snapshot or a named [EditRejection], never throws for a user action and never touches the engine. The `when` over
 * [ProjectEdit] is exhaustive with no `else` (`kotlin-control-flow`), so a new edit type fails to compile until it is
 * handled here. Rules: names per [ProjectNames]; colours cycle the six tints; clips on a track never overlap in beats
 * at the current tempo; a split stays strictly inside its clip and more than 0.01 s from both edges; a move stops
 * against its neighbours and never before zero; a cross-track move needs a free span; an import lands at the
 * requested start or the first free span after it; tempo and signature changes leave beats alone; a take replaces
 * what it covers on its track and keeps the parts outside its span (feature 010).
 */
object ProjectEditor {

    fun apply(project: Project, edit: ProjectEdit): EditResult = when (edit) {
        is AddTrack -> addTrack(project, edit)
        is RenameTrack -> renameTrack(project, edit)
        is SetLabel -> updateTrack(project, edit.id) { it.copy(label = edit.label) }
        is SetGain -> updateTrack(project, edit.id) { it.copy(gainDb = edit.gainDb) }
        is SetPan -> setPan(project, edit)
        is SetMuted -> updateTrack(project, edit.id) { it.copy(muted = edit.muted) }
        is SetSoloed -> updateTrack(project, edit.id) { it.copy(soloed = edit.soloed) }
        is MoveTrack -> moveTrack(project, edit)
        is DeleteTrack -> deleteTrack(project, edit)
        is ImportToTrack -> importToTrack(project, edit)
        is ImportAsNewTracks -> importAsNewTracks(project, edit)
        is SplitClip -> splitClip(project, edit)
        is MoveClip -> moveClip(project, edit)
        is DeleteClip -> deleteClip(project, edit)
        is SetTempo -> EditResult.Applied(project.copy(tempo = edit.tempo))
        is SetTimeSignature -> EditResult.Applied(project.copy(timeSignature = edit.timeSignature))
        is SetMasterVolume -> EditResult.Applied(project.copy(masterVolume = edit.volume))
        is RenameProject -> renameProject(project, edit)
        is SetDescription -> setDescription(project, edit)
        is SetKey -> EditResult.Applied(project.copy(key = edit.key))
        is RecordTake -> recordTake(project, edit)
        is SetCountIn -> EditResult.Applied(project.copy(countIn = edit.countIn))
    }

    // --- Tracks ------------------------------------------------------------------------------------------------------

    private fun addTrack(project: Project, edit: AddTrack): EditResult {
        if (project.tracks.size >= ProjectLimits.MAX_TRACKS) return EditResult.Rejected(EditRejection.TrackLimitReached)
        val track = Track.new(edit.id, Track.nextName(project.tracks.map { it.name }), nextColor(project.tracks))
        return EditResult.Applied(project.copy(tracks = project.tracks.adding(track)))
    }

    private fun renameTrack(project: Project, edit: RenameTrack): EditResult {
        project.findTrack(edit.id) ?: return EditResult.Rejected(EditRejection.UnknownTrack(edit.id))
        if (edit.name.trim().length > ProjectNames.MAX_TRACK_NAME) return EditResult.Rejected(EditRejection.TrackNameTooLong)
        val otherNames = project.tracks.filter { it.id != edit.id }.map { it.name }
        val name = ProjectNames.trackNameOrDefault(edit.name, otherNames)
        return updateTrack(project, edit.id) { it.copy(name = name) }
    }

    /** The pan (feature 012, spec FR-006): the track must exist and the value lie within −100..100. */
    private fun setPan(project: Project, edit: SetPan): EditResult {
        if (!edit.pan.inRange) return EditResult.Rejected(EditRejection.PanOutOfRange)
        return updateTrack(project, edit.id) { it.copy(pan = edit.pan) }
    }

    private fun moveTrack(project: Project, edit: MoveTrack): EditResult {
        val index = project.tracks.indexOfFirst { it.id == edit.id }
        if (index < 0) return EditResult.Rejected(EditRejection.UnknownTrack(edit.id))
        val target = when (edit.direction) {
            MoveDirection.Up -> index - 1
            MoveDirection.Down -> index + 1
        }
        if (target !in project.tracks.indices) return EditResult.Rejected(EditRejection.TrackAtEnd)
        val tracks = project.tracks.toMutableList()
        tracks[index] = project.tracks[target]
        tracks[target] = project.tracks[index]
        return EditResult.Applied(project.copy(tracks = tracks.toPersistentList()))
    }

    private fun deleteTrack(project: Project, edit: DeleteTrack): EditResult {
        val track = project.findTrack(edit.id) ?: return EditResult.Rejected(EditRejection.UnknownTrack(edit.id))
        val remaining = project.copy(tracks = project.tracks.removing(track))
        return EditResult.Applied(remaining.withoutUnusedAssets())
    }

    private inline fun updateTrack(project: Project, id: TrackId, transform: (Track) -> Track): EditResult {
        val index = project.tracks.indexOfFirst { it.id == id }
        if (index < 0) return EditResult.Rejected(EditRejection.UnknownTrack(id))
        return EditResult.Applied(project.copy(tracks = project.tracks.replacingAt(index, transform(project.tracks[index]))))
    }

    /** The tint after the last track's, or the first for an empty project: creation order, cycling (spec FR-022). */
    private fun nextColor(tracks: List<Track>): Int = tracks.lastOrNull()?.let { (it.colorIndex + 1) % Track.PALETTE_SIZE } ?: 0

    // --- Import ------------------------------------------------------------------------------------------------------

    private fun importToTrack(project: Project, edit: ImportToTrack): EditResult {
        val track = project.findTrack(edit.trackId) ?: return EditResult.Rejected(EditRejection.UnknownTrack(edit.trackId))
        val withAssets = project.copy(assets = project.assets.puttingAll(edit.assets))
        checkImport(withAssets, edit.clips)?.let { return EditResult.Rejected(it) }
        var clips = track.clips
        for (clip in edit.clips) {
            val length = withAssets.clipLengthBeats(clip).value
            val start = firstFreeStart(withAssets, clips, clip.startBeats.value.coerceAtLeast(0.0), length)
            clips = clips.insertSorted(clip.copy(startBeats = Beats(start)))
        }
        return updateTrack(withAssets, track.id) { it.copy(clips = clips) }
    }

    private fun importAsNewTracks(project: Project, edit: ImportAsNewTracks): EditResult {
        if (project.tracks.size + edit.tracks.size > ProjectLimits.MAX_TRACKS) return EditResult.Rejected(EditRejection.TrackLimitReached)
        val withAssets = project.copy(assets = project.assets.puttingAll(edit.assets))
        checkImport(withAssets, edit.tracks.map { it.clip })?.let { return EditResult.Rejected(it) }
        var tracks = withAssets.tracks
        var color = nextColor(tracks)
        for (item in edit.tracks) {
            val name = importedTrackName(item.name, tracks.map { it.name })
            val clip = item.clip.copy(startBeats = item.clip.startBeats.coerceAtLeastZero())
            tracks = tracks.adding(Track(id = item.trackId, name = name, colorIndex = color, clips = persistentListOf(clip)))
            color = (color + 1) % Track.PALETTE_SIZE
        }
        return EditResult.Applied(withAssets.copy(tracks = tracks))
    }

    /** Every clip's asset must be known and the clip bound respected. */
    private fun checkImport(project: Project, clips: List<Clip>): EditRejection? {
        for (clip in clips) if (clip.assetId !in project.assets) return EditRejection.UnknownAsset(clip.assetId)
        if (project.clipCount + clips.size > ProjectLimits.MAX_CLIPS) return EditRejection.ClipLimitReached
        return null
    }

    /** The file name trimmed and cut to the track-name limit; a blank name becomes "Track N" (spec FR-044, FR-020). */
    private fun importedTrackName(fileName: String, existingNames: List<String>): String =
        ProjectNames.trackNameOrDefault(fileName.trim().take(ProjectNames.MAX_TRACK_NAME), existingNames)

    /** The first start at or after [requested] where a clip [length] beats long overlaps none of [clips] (spec FR-044). */
    private fun firstFreeStart(project: Project, clips: List<Clip>, requested: Double, length: Double): Double {
        var candidate = requested
        val spans = clips.map { it.startBeats.value to project.clipEndBeats(it).value }.sortedBy { it.first }
        for ((start, end) in spans) {
            if (end <= candidate + EPSILON) continue
            if (start >= candidate + length - EPSILON) break
            candidate = end
        }
        return candidate
    }

    // --- Clips -------------------------------------------------------------------------------------------------------

    private fun splitClip(project: Project, edit: SplitClip): EditResult {
        val track = project.trackOf(edit.clipId) ?: return EditResult.Rejected(EditRejection.UnknownClip(edit.clipId))
        val clip = track.clips.first { it.id == edit.clipId }
        val asset = project.assets[clip.assetId] ?: return EditResult.Rejected(EditRejection.UnknownAsset(clip.assetId))
        val map = project.tempoMap
        val start = clip.startBeats.value
        val end = project.clipEndBeats(clip).value
        val at = edit.atBeats.value
        if (at <= start || at >= end) return EditResult.Rejected(EditRejection.SplitOutsideClip)
        val fromStart = map.beatsToSeconds(Beats(at - start))
        val toEnd = map.beatsToSeconds(Beats(end - at))
        if (fromStart <= ProjectLimits.MIN_SPLIT_EDGE_SECONDS || toEnd <= ProjectLimits.MIN_SPLIT_EDGE_SECONDS) {
            return EditResult.Rejected(EditRejection.SplitTooCloseToEdge)
        }
        val splitFrames = map.beatsToFrames(Beats(at - start), asset.sampleRate)
        if (splitFrames <= 0 || splitFrames >= clip.lengthFrames) return EditResult.Rejected(EditRejection.SplitTooCloseToEdge)
        val left = clip.copy(id = edit.leftId, lengthFrames = splitFrames)
        val right = clip.copy(
            id = edit.rightId,
            startBeats = clip.startBeats + map.framesToBeats(splitFrames, asset.sampleRate),
            offsetFrames = clip.offsetFrames + splitFrames,
            lengthFrames = clip.lengthFrames - splitFrames,
        )
        val clips = track.clips.removing(clip).insertSorted(left).insertSorted(right)
        return updateTrack(project, track.id) { it.copy(clips = clips) }
    }

    private fun moveClip(project: Project, edit: MoveClip): EditResult {
        val source = project.trackOf(edit.clipId) ?: return EditResult.Rejected(EditRejection.UnknownClip(edit.clipId))
        val target = project.findTrack(edit.toTrack) ?: return EditResult.Rejected(EditRejection.UnknownTrack(edit.toTrack))
        val clip = source.clips.first { it.id == edit.clipId }
        val length = project.clipLengthBeats(clip).value
        val requested = edit.startBeats.value.coerceAtLeast(0.0)
        if (source.id == target.id) {
            val others = source.clips.removing(clip)
            val currentStart = clip.startBeats.value
            val currentEnd = currentStart + length
            val previousEnd = others.map { project.clipEndBeats(it).value }.filter { it <= currentStart + EPSILON }.maxOrNull() ?: 0.0
            val nextStart = others.map { it.startBeats.value }.filter { it >= currentEnd - EPSILON }.minOrNull()
            val upper = nextStart?.let { it - length } ?: Double.MAX_VALUE
            val start = requested.coerceIn(previousEnd, max(previousEnd, upper))
            return updateTrack(project, source.id) { it.copy(clips = others.insertSorted(clip.copy(startBeats = Beats(start)))) }
        }
        if (!spanIsFree(project, target.clips, requested, length)) return EditResult.Rejected(EditRejection.SpanOccupied)
        val moved = clip.copy(startBeats = Beats(requested))
        val tracks = project.tracks.map { track ->
            when (track.id) {
                source.id -> track.copy(clips = track.clips.removing(clip))
                target.id -> track.copy(clips = track.clips.insertSorted(moved))
                else -> track
            }
        }
        return EditResult.Applied(project.copy(tracks = tracks.toPersistentList()))
    }

    private fun deleteClip(project: Project, edit: DeleteClip): EditResult {
        val track = project.trackOf(edit.clipId) ?: return EditResult.Rejected(EditRejection.UnknownClip(edit.clipId))
        val clip = track.clips.first { it.id == edit.clipId }
        val result = updateTrack(project, track.id) { it.copy(clips = it.clips.removing(clip)) }
        return when (result) {
            is EditResult.Applied -> EditResult.Applied(result.project.withoutUnusedAssets())
            is EditResult.Rejected -> result
        }
    }

    private fun spanIsFree(project: Project, clips: List<Clip>, start: Double, length: Double): Boolean =
        clips.none { other ->
            val otherStart = other.startBeats.value
            val otherEnd = project.clipEndBeats(other).value
            otherStart < start + length - EPSILON && start < otherEnd - EPSILON
        }

    private fun PersistentList<Clip>.insertSorted(clip: Clip): PersistentList<Clip> =
        (this + clip).sortedBy { it.startBeats.value }.toPersistentList()

    /** Drops asset entries no clip references (data-model.md section 1); their files go when the history lets them. */
    private fun Project.withoutUnusedAssets(): Project {
        val unused = unusedAssets
        if (unused.isEmpty()) return this
        return copy(assets = assets.filterKeys { it !in unused }.toPersistentMap())
    }

    // --- Recording ---------------------------------------------------------------------------------------------------

    /**
     * A take and its replacement as one edit (feature 010, spec FR-011 to FR-013): the asset merged, every clip on the
     * track overlapping `[start, start + length)` reduced to its parts outside the span by [splitClip]'s frame
     * arithmetic (a straddling clip keeps its outside part or parts, an inside clip goes), the take's clip inserted,
     * orphaned assets dropped. A clip straddling both ends keeps its id on its left part; the right part gets
     * `<take clip id>-after-<clip id>`, fixed by the edit so it replays and never collides with an earlier take's.
     */
    private fun recordTake(project: Project, edit: RecordTake): EditResult {
        val track = project.findTrack(edit.trackId) ?: return EditResult.Rejected(EditRejection.UnknownTrack(edit.trackId))
        if (edit.clip.lengthFrames.toDouble() / edit.asset.sampleRate < MIN_TAKE_SECONDS) return EditResult.Rejected(EditRejection.TakeTooShort)
        val withAsset = project.copy(assets = project.assets.putting(edit.assetId, edit.asset))
        val map = withAsset.tempoMap
        val take = edit.clip.toClip(edit.clipId, edit.startBeats)
        val spanStart = take.startBeats
        val spanEnd = spanStart + map.framesToBeats(take.lengthFrames, edit.asset.sampleRate)
        var clips = track.clips
        for (clip in withAsset.clipsOverlapping(track.id, spanStart, spanEnd)) {
            clips = clips.removing(clip)
            val asset = withAsset.assets.getValue(clip.assetId)
            val before = partBefore(map, clip, asset, spanStart)
            val after = partAfter(map, clip, asset, spanEnd)
            if (before != null) clips = clips.insertSorted(before)
            if (after != null) clips = clips.insertSorted(if (before == null) after else after.copy(id = ClipId("${edit.clipId.value}-after-${clip.id.value}")))
        }
        clips = clips.insertSorted(take)
        if (withAsset.clipCount - track.clips.size + clips.size > ProjectLimits.MAX_CLIPS) return EditResult.Rejected(EditRejection.ClipLimitReached)
        return when (val result = updateTrack(withAsset, track.id) { it.copy(clips = clips) }) {
            is EditResult.Applied -> EditResult.Applied(result.project.withoutUnusedAssets())
            is EditResult.Rejected -> result
        }
    }

    /**
     * The part of [clip] before [spanStart], its id kept, or null when there is none. Frames are rounded as a split
     * is; a rounding that would carry the part past the span start is pulled back one frame so the track never
     * overlaps at the current tempo.
     */
    private fun partBefore(map: TempoMap, clip: Clip, asset: AudioAsset, spanStart: Beats): Clip? {
        if (clip.startBeats.value >= spanStart.value - EPSILON) return null
        var frames = map.beatsToFrames(spanStart - clip.startBeats, asset.sampleRate)
        if ((clip.startBeats + map.framesToBeats(frames, asset.sampleRate)).value > spanStart.value + EPSILON) frames -= 1
        if (frames <= 0) return null
        return clip.copy(lengthFrames = frames)
    }

    /**
     * The part of [clip] after [spanEnd], its id kept for the caller to replace, or null when there is none. The
     * offset advances by the frames up to the span end and the start follows them, so the audio stays where it was;
     * a rounding that would leave the part inside the span is pushed on one frame.
     */
    private fun partAfter(map: TempoMap, clip: Clip, asset: AudioAsset, spanEnd: Beats): Clip? {
        val clipEnd = clip.startBeats + map.framesToBeats(clip.lengthFrames, asset.sampleRate)
        if (clipEnd.value <= spanEnd.value + EPSILON) return null
        var frames = map.beatsToFrames(spanEnd - clip.startBeats, asset.sampleRate)
        if ((clip.startBeats + map.framesToBeats(frames, asset.sampleRate)).value < spanEnd.value - EPSILON) frames += 1
        if (frames >= clip.lengthFrames) return null
        return clip.copy(
            startBeats = clip.startBeats + map.framesToBeats(frames, asset.sampleRate),
            offsetFrames = clip.offsetFrames + frames,
            lengthFrames = clip.lengthFrames - frames,
        )
    }

    // --- Project settings --------------------------------------------------------------------------------------------

    private fun renameProject(project: Project, edit: RenameProject): EditResult {
        if (!ProjectNames.isValidProjectName(edit.name)) return EditResult.Rejected(EditRejection.ProjectNameOutOfRange)
        return EditResult.Applied(project.copy(name = edit.name.trim()))
    }

    private fun setDescription(project: Project, edit: SetDescription): EditResult {
        if (!ProjectNames.isValidDescription(edit.description)) return EditResult.Rejected(EditRejection.DescriptionTooLong)
        return EditResult.Applied(project.copy(description = edit.description.trim()))
    }

    /** Absorbs the last-bit error of beat arithmetic so touching clips never count as overlapping. */
    private const val EPSILON = 1e-9

    /** A take shorter than this is discarded rather than placed (feature 010, spec FR-011). */
    private const val MIN_TAKE_SECONDS = 0.01
}
