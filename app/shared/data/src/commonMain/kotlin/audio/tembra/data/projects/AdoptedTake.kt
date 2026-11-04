package audio.tembra.data.projects

import audio.tembra.core.project.AudioAsset
import audio.tembra.core.project.AudioAssetId
import audio.tembra.core.project.Beats
import audio.tembra.core.project.ClipTemplate

/** The outcome of [TakeAdopter.adopt] (contracts/take-storage.md): what the controller turns into a `RecordTake` edit. */
sealed interface AdoptedTake {
    /**
     * The copy is in place at [audioPath] with its overview at [overviewPath] (or none, when the engine could not
     * build it: the repository reports that through `overviewStatus`); [clip] covers the whole take, named after the
     * manifest, and [startBeats] is where the take began.
     */
    data class Adopted(
        val assetId: AudioAssetId,
        val asset: AudioAsset,
        val clip: ClipTemplate,
        val startBeats: Beats,
        val audioPath: String,
        val overviewPath: String,
    ) : AdoptedTake

    /** The take kept no frames (cancelled, or shorter than 0.01 s): both staging files are gone. */
    data object Discarded : AdoptedTake

    /** The file could not be read, or disagrees with what the engine reported: the staging files stay for the next launch. */
    data class Failed(val reason: String) : AdoptedTake
}
