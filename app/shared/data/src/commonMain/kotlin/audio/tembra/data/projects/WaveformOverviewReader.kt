package audio.tembra.data.projects

import okio.BufferedSource
import okio.FileSystem
import okio.IOException
import okio.Path

/**
 * Parses the engine's little-endian `.peaks` file (contracts/engine-c-abi-v6.md): magic "TPK1", u16 version, u16
 * channels, u32 levels (4), u32 base bucket (256), i64 frames, u64 completed base buckets, then the levels. Bytes the
 * writer has not flushed yet read as silence; a missing or unrecognised file reads as null.
 */
object WaveformOverviewReader {

    private const val MAGIC = "TPK1"
    private const val HEADER_BYTES = 32L

    fun readHeader(fileSystem: FileSystem, path: Path): OverviewHeader? = try {
        fileSystem.read(path) { parseHeader(this) }
    } catch (failure: IOException) {
        null
    }

    fun read(fileSystem: FileSystem, path: Path): WaveformOverview? = try {
        fileSystem.read(path) {
            val header = parseHeader(this)
            if (header == null) {
                null
            } else {
                val levels = WaveformOverview.LEVEL_SIZES.map { size ->
                    val buckets = ((header.frames + size - 1) / size).toInt()
                    val data = ByteArray(buckets * header.channels * 2)
                    readAvailable(this, data)
                    val completed = if (header.isComplete) buckets else minOf(buckets.toLong(), header.completedBaseBuckets * WaveformOverview.BASE_BUCKET / size).toInt()
                    WaveformLevel(size, buckets, completed, header.channels, data)
                }
                WaveformOverview(header.channels, header.frames, header.completedBaseBuckets, levels)
            }
        }
    } catch (failure: IOException) {
        null
    }

    private fun parseHeader(source: BufferedSource): OverviewHeader? {
        if (!source.request(HEADER_BYTES)) return null
        if (source.readUtf8(4) != MAGIC) return null
        val version = source.readShortLe().toInt()
        val channels = source.readShortLe().toInt()
        val levels = source.readIntLe()
        val baseBucket = source.readIntLe()
        val frames = source.readLongLe()
        val completed = source.readLongLe()
        if (version != 1 || channels !in 1..8 || levels != WaveformOverview.LEVEL_SIZES.size || baseBucket != WaveformOverview.BASE_BUCKET || frames < 0) return null
        return OverviewHeader(channels, frames, completed.coerceAtLeast(0))
    }

    /** Fills [data] with what the file still holds; the rest stays zero (silence). */
    private fun readAvailable(source: BufferedSource, data: ByteArray) {
        var offset = 0
        while (offset < data.size) {
            val read = source.read(data, offset, data.size - offset)
            if (read == -1) return
            offset += read
        }
    }
}
