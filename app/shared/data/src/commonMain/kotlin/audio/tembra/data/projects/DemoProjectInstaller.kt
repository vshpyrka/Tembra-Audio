package audio.tembra.data.projects

import audio.tembra.core.project.AudioAssetId
import audio.tembra.core.project.AudioFormat
import audio.tembra.core.project.Project
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.HashingSource
import okio.buffer
import okio.use

/**
 * Writes the demo's audio copies into a project folder from the bundled loops and returns the demo document
 * (contracts/project-storage.md, `installDemo`). Each loop is copied through a SHA-256 hashing source and stored
 * under the hash it computes, which becomes the asset's content id: the document always names the bytes on the
 * device, and a test can feed any bytes. The loops' frame facts are the constants of [DemoProject].
 */
class DemoProjectInstaller(
    private val fileSystem: FileSystem,
    private val demoAudio: DemoAudioSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun install(folder: ProjectFolder, ids: DemoProjectIds = DemoProjectIds.random(folder.id)): Project = withContext(ioDispatcher) {
        fileSystem.createDirectories(folder.audio)
        val assetIds = HashMap<String, AudioAssetId>()
        val byteSizes = HashMap<String, Long>()
        for (loop in DemoProject.LOOPS) {
            val staging = folder.stagingAudioPath("demo-${loop.name}")
            var byteSize = 0L
            val hash = HashingSource.sha256(demoAudio.open(loop.name)).use { hashing ->
                fileSystem.sink(staging).buffer().use { sink ->
                    byteSize = sink.writeAll(hashing)
                }
                hashing.hash.hex()
            }
            val assetId = AudioAssetId(hash)
            val target = folder.audioPath(assetId, AudioFormat.Wav)
            if (fileSystem.exists(target)) fileSystem.delete(staging) else fileSystem.atomicMove(staging, target)
            assetIds[loop.name] = assetId
            byteSizes[loop.name] = byteSize
        }
        DemoProject.build(ids, assetIds, byteSizes)
    }
}
