package audio.tembra.data.projects

import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.core.project.AudioAsset
import audio.tembra.core.project.AudioAssetId
import audio.tembra.core.project.AudioFormat
import audio.tembra.core.project.Beats
import audio.tembra.core.project.Clip
import audio.tembra.core.project.ClipId
import audio.tembra.core.project.GainDb
import audio.tembra.core.project.Project
import audio.tembra.core.project.ProjectId
import audio.tembra.core.project.Track
import audio.tembra.core.project.TrackId
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import okio.Buffer
import okio.ByteString.Companion.toByteString
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Clock
import kotlin.time.Instant

/** Builders shared by the data tests: deterministic projects, content ids and `.peaks` files. */
internal object DataFixtures {

    fun assetId(bytes: ByteArray): AudioAssetId = AudioAssetId(bytes.toByteString().sha256().hex())

    /** 48 kHz stereo, 64 beats at 120 BPM (32 s). */
    fun stereoAsset(byteSize: Long = 6_144_044): AudioAsset =
        AudioAsset(fileName = "loop.wav", format = AudioFormat.Wav, channels = 2, sampleRate = 48000, frames = 1_536_000, byteSize = byteSize)

    fun clip(n: Int, assetId: AudioAssetId, start: Double = 0.0, offset: Long = 0, length: Long = 1_536_000, name: String = "loop"): Clip =
        Clip(ClipId("clip-$n"), assetId, Beats(start), offset, length, name)

    fun track(n: Int, vararg clips: Clip, name: String = "Track $n", colorIndex: Int = (n - 1) % 6, gainDb: GainDb = GainDb.Unity, muted: Boolean = false): Track =
        Track(id = TrackId("track-$n"), name = name, colorIndex = colorIndex, gainDb = gainDb, muted = muted, clips = clips.toList().toPersistentList())

    fun project(id: String = "project-1", name: String = "Song", tracks: List<Track> = emptyList(), assets: Map<AudioAssetId, AudioAsset> = emptyMap(), tempo: Tempo = Tempo(120)): Project =
        Project(
            id = ProjectId(id), name = name, tempo = tempo, timeSignature = TimeSignature.FourFour,
            assets = assets.toPersistentMap(), tracks = tracks.toPersistentList(),
        )

    /** A project with [count] tracks, each holding one clip of one shared asset, for the round-trip tests. */
    fun manyTracks(count: Int): Project {
        val id = AudioAssetId("c".repeat(64))
        val tracks = (1..count).map { n -> track(n, clip(n, id, start = (n - 1) * 4.0, name = "take $n"), name = "Track $n") }
        return project(name = "Thirty-two", tracks = tracks, assets = mapOf(id to stereoAsset()))
    }

    /**
     * A `.peaks` file (contracts/engine-c-abi-v6.md): the 32-byte header, then int8 min/max per channel per bucket for
     * the levels 256, 1024, 4096 and 16 384. [amplitude] gives the peak 0..127 at a frame per channel; each bucket holds
     * ±max over its frames, sampled every [sampleStep] frames. [completedBaseBuckets] defaults to every bucket; with
     * [truncateAfterBase] the pyramid levels are left out, as the writer leaves them while still running.
     */
    fun peaks(
        channels: Int,
        frames: Long,
        completedBaseBuckets: Long = (frames + 255) / 256,
        truncateAfterBase: Boolean = false,
        sampleStep: Long = 64,
        amplitude: (frame: Long, channel: Int) -> Int,
    ): ByteArray {
        val buffer = Buffer()
        buffer.writeUtf8("TPK1")
        buffer.writeShortLe(1)
        buffer.writeShortLe(channels)
        buffer.writeIntLe(4)
        buffer.writeIntLe(256)
        buffer.writeLongLe(frames)
        buffer.writeLongLe(completedBaseBuckets)
        val levels = if (truncateAfterBase) listOf(256L) else listOf(256L, 1024L, 4096L, 16384L)
        for (size in levels) {
            val buckets = (frames + size - 1) / size
            for (bucket in 0 until buckets) {
                for (channel in 0 until channels) {
                    var peak = 0
                    var frame = bucket * size
                    val end = min(frames, (bucket + 1) * size)
                    while (frame < end) {
                        peak = max(peak, amplitude(frame, channel))
                        frame += sampleStep
                    }
                    buffer.writeByte(-peak)
                    buffer.writeByte(peak)
                }
            }
        }
        return buffer.readByteArray()
    }
}

/** A settable clock for `FakeFileSystem` so the tests control file modification times. */
internal class FakeClock(var nowMs: Long = 1_700_000_000_000L) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(nowMs)
}
