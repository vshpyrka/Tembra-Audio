package audio.tembra.data.projects

import audio.tembra.core.metronome.Tempo
import audio.tembra.core.project.AudioAssetId
import audio.tembra.core.project.ClipId
import audio.tembra.core.project.ProjectLimits
import audio.tembra.core.project.ProjectNames
import audio.tembra.core.project.TrackId

/** Every named reason a well-formed document is refused (contracts/project-document.md, validation on read). */
sealed interface ValidationFailure {
    val message: String

    data class DuplicateId(val id: String) : ValidationFailure {
        override val message: String get() = "Duplicate id \"$id\""
    }

    data class MissingAsset(val clipId: ClipId, val assetId: AudioAssetId) : ValidationFailure {
        override val message: String get() = "Clip ${clipId.value} refers to an audio copy the document does not list"
    }

    data class ClipOutsideAsset(val clipId: ClipId) : ValidationFailure {
        override val message: String get() = "Clip ${clipId.value} reaches past the end of its audio"
    }

    data class TooManyTracks(val count: Int) : ValidationFailure {
        override val message: String get() = "$count tracks is more than the ${ProjectLimits.MAX_TRACKS} the engine holds"
    }

    data class TooManyClips(val count: Int) : ValidationFailure {
        override val message: String get() = "$count clips is more than the ${ProjectLimits.MAX_CLIPS} the engine holds"
    }

    data class ProjectNameLength(val length: Int) : ValidationFailure {
        override val message: String get() = "The project name must be 1 to ${ProjectNames.MAX_PROJECT_NAME} characters, was $length"
    }

    data class DescriptionLength(val length: Int) : ValidationFailure {
        override val message: String get() = "The description must be at most ${ProjectNames.MAX_DESCRIPTION} characters, was $length"
    }

    data class TrackNameLength(val trackId: TrackId, val length: Int) : ValidationFailure {
        override val message: String get() = "Track ${trackId.value} has a name of $length characters, not 1 to ${ProjectNames.MAX_TRACK_NAME}"
    }

    data class TempoOutOfRange(val bpm: Int) : ValidationFailure {
        override val message: String get() = "Tempo $bpm is outside ${Tempo.MIN}..${Tempo.MAX}"
    }

    data class UnknownTimeSignature(val label: String) : ValidationFailure {
        override val message: String get() = "Unknown time signature \"$label\""
    }

    data class AssetFactsInvalid(val assetId: AudioAssetId) : ValidationFailure {
        override val message: String get() = "Audio copy ${assetId.sha256.take(8)} has impossible channel, rate or frame facts"
    }
}
