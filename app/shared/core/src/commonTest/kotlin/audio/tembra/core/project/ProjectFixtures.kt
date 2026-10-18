package audio.tembra.core.project

import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap

/** Deterministic builders shared by the core project tests: fixed ids, one stereo and one mono asset. */
internal object ProjectFixtures {
    val stereoAssetId = AudioAssetId("a".repeat(64))
    val monoAssetId = AudioAssetId("b".repeat(64))

    /** 48 kHz stereo, exactly 64 beats long at 120 BPM (32 s = 1 536 000 frames). */
    val stereoAsset = AudioAsset(fileName = "loop.wav", format = AudioFormat.Wav, channels = 2, sampleRate = 48000, frames = 1_536_000, byteSize = 6_144_044)

    /** 44.1 kHz mono, 10 s (441 000 frames). */
    val monoAsset = AudioAsset(fileName = "hit.flac", format = AudioFormat.Flac, channels = 1, sampleRate = 44100, frames = 441_000, byteSize = 400_000)

    fun trackId(n: Int): TrackId = TrackId("track-$n")

    fun clipId(n: Int): ClipId = ClipId("clip-$n")

    fun clip(n: Int, start: Double, assetId: AudioAssetId = stereoAssetId, offset: Long = 0, length: Long = 1_536_000, name: String = "loop"): Clip =
        Clip(id = clipId(n), assetId = assetId, startBeats = Beats(start), offsetFrames = offset, lengthFrames = length, name = name)

    fun track(n: Int, vararg clips: Clip, name: String = "Track $n", colorIndex: Int = (n - 1) % 6, muted: Boolean = false, soloed: Boolean = false, gainDb: GainDb = GainDb.Unity): Track =
        Track(id = trackId(n), name = name, label = null, colorIndex = colorIndex, gainDb = gainDb, muted = muted, soloed = soloed, clips = clips.toList().toPersistentList())

    fun project(vararg tracks: Track, tempo: Tempo = Tempo(120), signature: TimeSignature = TimeSignature.FourFour, assets: Map<AudioAssetId, AudioAsset> = mapOf(stereoAssetId to stereoAsset, monoAssetId to monoAsset)): Project =
        Project(
            id = ProjectId("project-1"),
            name = "Test project",
            description = "",
            key = null,
            tempo = tempo,
            timeSignature = signature,
            masterVolume = Percent(80),
            metronomeOn = false,
            assets = assets.toPersistentMap(),
            tracks = tracks.toList().toPersistentList(),
        )

    val empty: Project = Project(
        id = ProjectId("project-0"),
        name = "Empty",
        tempo = Tempo(120),
        timeSignature = TimeSignature.FourFour,
        tracks = persistentListOf(),
        assets = persistentMapOf(),
    )
}
