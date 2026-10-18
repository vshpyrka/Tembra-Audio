package audio.tembra.data.projects

import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.core.project.AudioAsset
import audio.tembra.core.project.AudioAssetId
import audio.tembra.core.project.AudioFormat
import audio.tembra.core.project.Beats
import audio.tembra.core.project.Clip
import audio.tembra.core.project.InstrumentLabel
import audio.tembra.core.project.MusicalKey
import audio.tembra.core.project.Percent
import audio.tembra.core.project.Project
import audio.tembra.core.project.Track
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap

/**
 * The demo project of spec FR-003 and FR-045 (research R18): four 16-bar loops at 124 BPM in 4/4, D minor, with
 * the pad split in two so the demo has five clips. The loops are the bundled `files/demo/<name>.wav` (1 486 452 frames
 * at 48 kHz, 16-bit; drums and bass mono, keys and pad stereo), whose facts and content ids are fixed here; the
 * installer hashes what it copies, so an installed document always names the bytes on the device.
 */
object DemoProject {
    const val NAME = "Demo project"
    const val DESCRIPTION = "Four loops to play with"
    val KEY: MusicalKey = MusicalKey.DMinor
    val TEMPO: Tempo = Tempo(124)
    val SIGNATURE: TimeSignature = TimeSignature.FourFour
    val MASTER_VOLUME: Percent = Percent(80)
    const val SAMPLE_RATE = 48_000
    const val FRAMES = 1_486_452L
    const val PAD_SPLIT_FRAMES = 743_226L
    const val PAD_SECOND_HALF_START_BEATS = 32.0
    const val CLIP_COUNT = 5

    val LOOPS: List<DemoLoop> = listOf(
        DemoLoop("drums", "Drums", InstrumentLabel.Drums, channels = 1),
        DemoLoop("bass", "Bass", InstrumentLabel.Bass, channels = 1),
        DemoLoop("keys", "Keys", InstrumentLabel.Keys, channels = 2),
        DemoLoop("pad", "Pad", InstrumentLabel.Synth, channels = 2),
    )

    /** SHA-256 content ids of the bundled loops. */
    val BUNDLED_ASSET_IDS: Map<String, AudioAssetId> = mapOf(
        "drums" to AudioAssetId("5f495ce910cc8fb7f3a1d16f3098edc61b53f8c64c7db2e4f4894cd870ce7638"),
        "bass" to AudioAssetId("a5c8eaa3534a1f79d0f7477e6b11fe272c9cf3d80e1ea79454c04701b6916a6d"),
        "keys" to AudioAssetId("781b436a38c04b4588d8345226291f1b0816127b8fcf589e3ddc7ff2ef62cebf"),
        "pad" to AudioAssetId("ab752321439c50f4c0ff2b990d07ce4e58d5345f56ce3e1e5adfca2206109869"),
    )

    /** Byte sizes of the bundled loops: 16-bit PCM WAV, 44-byte header. */
    val BUNDLED_BYTE_SIZES: Map<String, Long> = LOOPS.associate { it.name to if (it.channels == 1) 2_972_948L else 5_945_852L }

    /** The demo document for the given identities and audio facts. */
    fun build(
        ids: DemoProjectIds,
        assetIds: Map<String, AudioAssetId> = BUNDLED_ASSET_IDS,
        byteSizes: Map<String, Long> = BUNDLED_BYTE_SIZES,
    ): Project {
        val assets = LOOPS.associate { loop ->
            assetIds.getValue(loop.name) to AudioAsset(
                fileName = loop.fileName, format = AudioFormat.Wav, channels = loop.channels,
                sampleRate = SAMPLE_RATE, frames = FRAMES, byteSize = byteSizes.getValue(loop.name),
            )
        }
        var clipIndex = 0
        val tracks = LOOPS.mapIndexed { index, loop ->
            val assetId = assetIds.getValue(loop.name)
            val clips = if (loop.name == "pad") {
                persistentListOf(
                    Clip(ids.clipIds[clipIndex++], assetId, Beats.Zero, 0, PAD_SPLIT_FRAMES, loop.name),
                    Clip(ids.clipIds[clipIndex++], assetId, Beats(PAD_SECOND_HALF_START_BEATS), PAD_SPLIT_FRAMES, FRAMES - PAD_SPLIT_FRAMES, loop.name),
                )
            } else {
                persistentListOf(Clip(ids.clipIds[clipIndex++], assetId, Beats.Zero, 0, FRAMES, loop.name))
            }
            Track(id = ids.trackIds[index], name = loop.trackName, label = loop.label, colorIndex = index, clips = clips)
        }
        return Project(
            id = ids.projectId,
            name = NAME,
            description = DESCRIPTION,
            key = KEY,
            tempo = TEMPO,
            timeSignature = SIGNATURE,
            masterVolume = MASTER_VOLUME,
            metronomeOn = false,
            assets = assets.toPersistentMap(),
            tracks = tracks.toPersistentList(),
        )
    }
}
