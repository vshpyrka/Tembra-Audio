package audio.tembra.data.projects

import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.core.project.Project
import audio.tembra.core.project.ProjectLimits
import audio.tembra.core.project.ProjectNames
import audio.tembra.core.project.clipCount

/**
 * The document rules a decoded [Project] must satisfy (contracts/project-document.md, validation on read): unique
 * ids, every clip's asset listed and the clip inside it, the engine's track and clip bounds, name lengths, plausible
 * asset facts. Tempo and signature are typed, so their range is checked on the raw JSON by the codec through
 * [validateTempo] and [validateTimeSignature]. Returns every failure, empty for a valid project.
 */
object ProjectValidator {

    fun validate(project: Project): List<ValidationFailure> {
        val failures = ArrayList<ValidationFailure>()
        val seen = HashSet<String>()
        for (track in project.tracks) if (!seen.add(track.id.value)) failures += ValidationFailure.DuplicateId(track.id.value)
        for (track in project.tracks) for (clip in track.clips) if (!seen.add(clip.id.value)) failures += ValidationFailure.DuplicateId(clip.id.value)
        for (track in project.tracks) {
            for (clip in track.clips) {
                val asset = project.assets[clip.assetId]
                if (asset == null) {
                    failures += ValidationFailure.MissingAsset(clip.id, clip.assetId)
                } else if (!clip.fitsWithin(asset)) {
                    failures += ValidationFailure.ClipOutsideAsset(clip.id)
                }
            }
        }
        if (project.tracks.size > ProjectLimits.MAX_TRACKS) failures += ValidationFailure.TooManyTracks(project.tracks.size)
        val clips = project.clipCount
        if (clips > ProjectLimits.MAX_CLIPS) failures += ValidationFailure.TooManyClips(clips)
        if (!ProjectNames.isValidProjectName(project.name)) failures += ValidationFailure.ProjectNameLength(project.name.trim().length)
        if (!ProjectNames.isValidDescription(project.description)) failures += ValidationFailure.DescriptionLength(project.description.trim().length)
        for (track in project.tracks) {
            if (!ProjectNames.isValidTrackName(track.name)) failures += ValidationFailure.TrackNameLength(track.id, track.name.trim().length)
            if (!track.pan.inRange) failures += ValidationFailure.PanOutOfRange(track.id, track.pan.percent)
        }
        for ((id, asset) in project.assets) {
            if (asset.channels !in 1..2 || asset.sampleRate <= 0 || asset.frames <= 0 || asset.byteSize < 0) failures += ValidationFailure.AssetFactsInvalid(id)
        }
        return failures
    }

    fun validateTempo(bpm: Int): ValidationFailure? = if (bpm in Tempo.MIN..Tempo.MAX) null else ValidationFailure.TempoOutOfRange(bpm)

    fun validateTimeSignature(label: String): ValidationFailure? =
        if (TimeSignature.entries.any { it.label == label }) null else ValidationFailure.UnknownTimeSignature(label)
}
