package audio.tembra.data.metronome

import audio.tembra.data.AppDirectories
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.IOException
import okio.Path.Companion.toPath
import okio.Source
import okio.buffer
import okio.use

/**
 * [CustomClickSoundStore] over Okio. The `metronome` directory is created when the store is built, so the staging
 * path can be written to straight away; the file operations run on [Dispatchers.IO].
 */
class OkioCustomClickSoundStore(
    directories: AppDirectories,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) : CustomClickSoundStore {

    private val directory = directories.documents.toPath() / "metronome"
    private val file = directory / "custom.wav"
    private val staging = directory / "custom.wav.tmp"

    override val path: String = file.toString()
    override val stagingPath: String = staging.toString()

    init {
        fileSystem.createDirectories(directory)
    }

    override suspend fun stage(open: suspend () -> Source): StageResult = withContext(Dispatchers.IO) {
        fileSystem.createDirectories(directory)
        val outcome = try {
            open().use { source -> copyCapped(source) }
        } catch (failure: IOException) {
            StageResult.Unreadable
        } catch (failure: IllegalStateException) {
            // A platform handle that was revoked or never granted.
            StageResult.Unreadable
        }
        if (outcome != StageResult.Staged) fileSystem.delete(staging)
        outcome
    }

    /** Streams at most [MAX_STAGED_BYTES] into the staging file; a longer source is cut short and refused. */
    private fun copyCapped(source: Source): StageResult {
        fileSystem.sink(staging).buffer().use { sink ->
            var total = 0L
            while (true) {
                val read = source.read(sink.buffer, COPY_CHUNK)
                if (read < 0L) break
                total += read
                if (total > MAX_STAGED_BYTES) return StageResult.TooLarge
                sink.emit()
            }
        }
        return StageResult.Staged
    }

    override suspend fun commit(): Boolean = withContext(Dispatchers.IO) {
        if (!fileSystem.exists(staging)) return@withContext false
        fileSystem.createDirectories(directory)
        fileSystem.delete(file)
        fileSystem.atomicMove(staging, file)
        true
    }

    override suspend fun exists(): Boolean = withContext(Dispatchers.IO) { fileSystem.exists(file) }

    override suspend fun delete() = withContext(Dispatchers.IO) { fileSystem.delete(file) }

    override suspend fun deleteStaging() = withContext(Dispatchers.IO) { fileSystem.delete(staging) }

    private companion object {
        /** The largest custom sound accepted: 64 MB (contracts/click-sounds.md). */
        const val MAX_STAGED_BYTES = 64L * 1024 * 1024
        const val COPY_CHUNK = 64L * 1024
    }
}
