package audio.tembra.data.projects

import audio.tembra.core.project.AudioAsset
import audio.tembra.core.project.AudioAssetId
import audio.tembra.core.project.AudioFormat
import audio.tembra.core.project.ClipTemplate
import audio.tembra.engine.AudioInfo
import audio.tembra.engine.TakeInfo
import audio.tembra.engine.TimelineAudio
import audio.tembra.engine.perf.PerfSection
import audio.tembra.engine.perf.PerfTrace
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.FileSystem
import okio.HashingSource
import okio.IOException
import okio.Path
import okio.use
import kotlin.math.abs

/**
 * Turns a staged take into an audio copy the way an import ends (contracts/take-storage.md "TakeAdopter.adopt";
 * research R14), on the IO dispatcher: a take without frames is discarded with its manifest; otherwise the staging
 * file is inspected in place and must agree with what the engine reported (channels, rate, frames within one block),
 * hashed through SHA-256 in 64 KiB chunks as [AudioImporter] hashes, renamed to `audio/<sha256>.wav` (an identical
 * copy is reused and the staging file deleted), given its `.peaks` overview inside `timeline.waveform`, and the
 * manifest is deleted last. A disagreement or a read failure leaves both staging files for the next launch.
 */
class TakeAdopter(
    private val fileSystem: FileSystem,
    private val audio: TimelineAudio,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun adopt(folder: ProjectFolder, manifest: TakeManifest, takeInfo: TakeInfo): AdoptedTake = withContext(ioDispatcher) {
        val staging = folder.takeStagingPath(manifest.token)
        val manifestPath = folder.takeManifestPath(manifest.token)
        try {
            if (takeInfo.frames == 0L) {
                fileSystem.delete(staging)
                fileSystem.delete(manifestPath)
                return@withContext AdoptedTake.Discarded
            }
            val info = audio.inspect(staging.toString()) ?: return@withContext AdoptedTake.Failed("The take's file $staging cannot be read")
            disagreement(info, takeInfo)?.let { return@withContext AdoptedTake.Failed(it) }
            val hashed = hash(staging)
            val assetId = AudioAssetId(hashed.sha256)
            val target = folder.audioPath(assetId, AudioFormat.Wav)
            val isNew = !fileSystem.exists(target)
            if (isNew) fileSystem.atomicMove(staging, target) else fileSystem.delete(staging)
            val asset = AudioAsset(
                fileName = target.name,
                format = AudioFormat.Wav,
                channels = info.channels,
                sampleRate = info.sampleRate,
                frames = info.frames,
                byteSize = hashed.byteSize,
            )
            val overviewPath = folder.overviewPath(assetId)
            if (isNew) {
                fileSystem.createDirectories(folder.cacheRoot)
                PerfTrace.section(PerfSection.TIMELINE_WAVEFORM) { audio.buildOverview(target.toString(), overviewPath.toString()) }
            }
            fileSystem.delete(manifestPath)
            AdoptedTake.Adopted(
                assetId = assetId,
                asset = asset,
                clip = ClipTemplate(assetId = assetId, name = manifest.takeName, offsetFrames = 0, lengthFrames = info.frames),
                startBeats = manifest.startBeats,
                audioPath = target.toString(),
                overviewPath = overviewPath.toString(),
            )
        } catch (failure: IOException) {
            AdoptedTake.Failed(failure.message ?: "The take could not be adopted")
        }
    }

    /** Why the file and the engine's report disagree; null when they agree (the frames within the last partial block). */
    private fun disagreement(info: AudioInfo, takeInfo: TakeInfo): String? = when {
        info.format != AudioFormat.Wav -> "The take's file is ${info.format.name}, not WAV"
        info.channels != takeInfo.channels -> "The take's file has ${info.channels} channels, the engine reported ${takeInfo.channels}"
        info.sampleRate != takeInfo.sampleRate -> "The take's file is at ${info.sampleRate} Hz, the engine reported a rate of ${takeInfo.sampleRate} Hz"
        abs(info.frames - takeInfo.frames) > FRAME_TOLERANCE -> "The take's file holds ${info.frames} frames, the engine reported ${takeInfo.frames}"
        else -> null
    }

    /** Streams the file through a SHA-256 source in 64 KiB chunks; the bytes themselves stay where they are. */
    private fun hash(path: Path): Hashed {
        HashingSource.sha256(fileSystem.source(path)).use { hashing ->
            val chunk = Buffer()
            var total = 0L
            while (true) {
                val read = hashing.read(chunk, CHUNK_BYTES)
                if (read == -1L) break
                total += read
                chunk.clear()
            }
            return Hashed(hashing.hash.hex(), total)
        }
    }

    private class Hashed(val sha256: String, val byteSize: Long)

    companion object {
        /** One recorder block: the header's sizes were refreshed at most a block before the end. */
        const val FRAME_TOLERANCE = 4096L
        private const val CHUNK_BYTES = 64L * 1024
    }
}
