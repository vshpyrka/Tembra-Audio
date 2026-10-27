package audio.tembra.data.metronome

import okio.Buffer
import okio.Source
import okio.use

/**
 * In-memory store for tests: [present] answers [exists], [calls] records the operations, [files] holds staged and
 * committed bytes by path. [stage] reads the picked bytes into [files] under [stagingPath]; [stageResult] forces a
 * refusal, leaving the partial bytes behind as a platform copy cut short would, so a test can prove they are removed.
 */
class FakeCustomClickSoundStore(var present: Boolean = false) : CustomClickSoundStore {
    override val path: String = "/documents/metronome/custom.wav"
    override val stagingPath: String = "$path.tmp"

    val files: MutableMap<String, ByteArray> = mutableMapOf()
    val calls: MutableList<String> = mutableListOf()

    /** When set, what [stage] answers instead of [StageResult.Staged]. */
    var stageResult: StageResult? = null

    override suspend fun stage(open: suspend () -> Source): StageResult {
        calls += "stage"
        val bytes = Buffer().also { buffer -> open().use { source -> buffer.writeAll(source) } }.readByteArray()
        files[stagingPath] = bytes
        return stageResult ?: StageResult.Staged
    }

    override suspend fun commit(): Boolean {
        calls += "commit"
        val staged = files.remove(stagingPath) ?: return false
        files[path] = staged
        present = true
        return true
    }

    override suspend fun exists(): Boolean {
        calls += "exists"
        return present
    }

    override suspend fun delete() {
        calls += "delete"
        files.remove(path)
        present = false
    }

    override suspend fun deleteStaging() {
        calls += "deleteStaging"
        files.remove(stagingPath)
    }
}
