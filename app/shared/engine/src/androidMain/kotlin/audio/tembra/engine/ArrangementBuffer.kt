package audio.tembra.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * One direct `ByteBuffer` laid out as the C `tembra_arrangement` struct (contracts/engine-c-abi-v6.md), reused by
 * every [NativeAudioEngine.setArrangement] so a snapshot crosses JNI in one call with no per-clip traffic.
 * Layout (natural alignment, 8-byte records): `track_count` at 0, `clip_count` at 4, 64 `tembra_track_state`
 * (`float gain_linear`, `uint32 audible`) from 8, then 1024 `tembra_clip_state` (`uint32 track`, `int32 source`,
 * `double start_beats`, `int64 offset_frames`, `int64 length_frames`) from 520; 33 288 bytes in all.
 */
internal class ArrangementBuffer {
    val buffer: ByteBuffer = ByteBuffer.allocateDirect(SIZE).order(ByteOrder.nativeOrder())

    /** Writes [snapshot] into the buffer; the counts bound what the engine reads, the rest is left as is. */
    fun fill(snapshot: ArrangementSnapshot) {
        buffer.putInt(0, snapshot.tracks.size)
        buffer.putInt(4, snapshot.clips.size)
        snapshot.tracks.forEachIndexed { index, track ->
            val at = TRACKS_OFFSET + index * TRACK_SIZE
            buffer.putFloat(at, track.gainLinear)
            buffer.putInt(at + 4, if (track.audible) 1 else 0)
        }
        snapshot.clips.forEachIndexed { index, clip ->
            val at = CLIPS_OFFSET + index * CLIP_SIZE
            buffer.putInt(at, clip.trackIndex)
            buffer.putInt(at + 4, clip.source.id)
            buffer.putDouble(at + 8, clip.startBeats)
            buffer.putLong(at + 16, clip.offsetFrames)
            buffer.putLong(at + 24, clip.lengthFrames)
        }
    }

    companion object {
        const val TRACK_SIZE = 8
        const val CLIP_SIZE = 32
        const val TRACKS_OFFSET = 8
        const val CLIPS_OFFSET = TRACKS_OFFSET + ArrangementSnapshot.MAX_TRACKS * TRACK_SIZE
        const val SIZE = CLIPS_OFFSET + ArrangementSnapshot.MAX_CLIPS * CLIP_SIZE
    }
}
