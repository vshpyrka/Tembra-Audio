package audio.tembra.data.metronome

import audio.tembra.data.AppDirectories
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath

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
}
