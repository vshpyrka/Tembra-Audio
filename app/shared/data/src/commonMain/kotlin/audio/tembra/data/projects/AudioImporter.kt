package audio.tembra.data.projects

import audio.tembra.core.project.AudioAsset
import audio.tembra.core.project.AudioAssetId
import audio.tembra.core.project.ClipTemplate
import audio.tembra.engine.TimelineAudio
import audio.tembra.engine.perf.PerfSection
import audio.tembra.engine.perf.PerfTrace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.FileSystem
import okio.HashingSource
import okio.Path
import okio.buffer
import okio.use
import kotlin.uuid.Uuid

/**
 * The import pipeline of contracts/project-storage.md (spec FR-044), on the IO dispatcher: the picked bytes are
 * copied through a SHA-256 hashing source into `audio/.staging-<uuid>`, refused as [ImportRefusal.TooLarge] past
 * [maxBytes] before any decoding, moved to `.staging-<sha256>` and inspected by the engine, refused for more than
 * two channels, more than 30 minutes or an unreadable file, then renamed to `audio/<sha256>.<ext>`; the same bytes
 * imported again reuse the copy. The overview is built once per new copy inside `timeline.waveform`. No staging
 * file survives a refusal or a failure. The extension decides [ImportRefusal.UnsupportedFormat] up front because
 * the picker's MIME filter is advisory; the engine's inspection decides the stored format.
 */
class AudioImporter(
    private val fileSystem: FileSystem,
    private val audio: TimelineAudio,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
) {
    /** Runs the pipeline per file, one staging file at a time, and returns every result in order. */
    suspend fun importAll(folder: ProjectFolder, files: List<PickedFile>): List<ImportResult> = files.map { import(folder, it) }

    suspend fun import(folder: ProjectFolder, file: PickedFile): ImportResult = withContext(ioDispatcher) {
        val extension = file.displayName.substringAfterLast('.', "").lowercase()
        if (extension !in SUPPORTED_EXTENSIONS) return@withContext ImportResult.Refused(file.displayName, ImportRefusal.UnsupportedFormat)
        fileSystem.createDirectories(folder.audio)
        val staging = folder.stagingAudioPath(Uuid.random().toString())
        var hashed: Path? = null
        try {
            val copied = copyAndHash(file, staging) ?: return@withContext ImportResult.Refused(file.displayName, ImportRefusal.TooLarge)
            val inspected = folder.stagingAudioPath(copied.sha256)
            fileSystem.atomicMove(staging, inspected)
            hashed = inspected
            val info = audio.inspect(inspected.toString()) ?: return@withContext ImportResult.Refused(file.displayName, ImportRefusal.Unreadable)
            if (info.channels > MAX_CHANNELS) return@withContext ImportResult.Refused(file.displayName, ImportRefusal.TooManyChannels)
            if (info.durationSeconds > MAX_SECONDS) return@withContext ImportResult.Refused(file.displayName, ImportRefusal.TooLong)
            val assetId = AudioAssetId(copied.sha256)
            val target = folder.audioPath(assetId, info.format)
            val isNew = !fileSystem.exists(target)
            if (isNew) fileSystem.atomicMove(inspected, target) else fileSystem.delete(inspected)
            hashed = null
            val asset = AudioAsset(file.displayName, info.format, info.channels, info.sampleRate, info.frames, copied.byteSize)
            val overviewBuilt = if (isNew) {
                fileSystem.createDirectories(folder.cacheRoot)
                PerfTrace.section(PerfSection.TIMELINE_WAVEFORM) { audio.buildOverview(target.toString(), folder.overviewPath(assetId).toString()) }
            } else {
                true
            }
            ImportResult.Accepted(assetId, asset, ClipTemplate.forAsset(assetId, asset), overviewBuilt, target.toString(), folder.overviewPath(assetId).toString())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            ImportResult.Refused(file.displayName, ImportRefusal.Unreadable)
        } finally {
            fileSystem.delete(staging)
            hashed?.let { fileSystem.delete(it) }
        }
    }

    /** Streams the picked bytes into [staging] while hashing; null once the size passes [maxBytes]. */
    private suspend fun copyAndHash(file: PickedFile, staging: Path): Copied? {
        HashingSource.sha256(file.open()).use { hashing ->
            fileSystem.sink(staging).buffer().use { sink ->
                val chunk = Buffer()
                var total = 0L
                while (true) {
                    val read = hashing.read(chunk, CHUNK_BYTES)
                    if (read == -1L) break
                    total += read
                    if (total > maxBytes) return null
                    sink.write(chunk, read)
                }
                sink.flush()
                return Copied(hashing.hash.hex(), total)
            }
        }
    }

    private class Copied(val sha256: String, val byteSize: Long)

    companion object {
        /** The early guard of contracts/project-storage.md step 2 (spec FR-044: larger than 500 MB is refused). */
        const val DEFAULT_MAX_BYTES: Long = 500L * 1024 * 1024
        const val MAX_CHANNELS = 2
        const val MAX_SECONDS = 1800.0
        private const val CHUNK_BYTES = 64L * 1024
        private val SUPPORTED_EXTENSIONS = setOf("wav", "wave", "flac", "mp3")
    }
}
