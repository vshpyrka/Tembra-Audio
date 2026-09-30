package audio.tembra.data.metronome

/** In-memory store for tests: [present] answers [exists], [calls] records the operations, [files] holds staged and committed bytes by path. */
class FakeCustomClickSoundStore(var present: Boolean = false) : CustomClickSoundStore {
    override val path: String = "/documents/metronome/custom.wav"
    override val stagingPath: String = "$path.tmp"

    val files: MutableMap<String, ByteArray> = mutableMapOf()
    val calls: MutableList<String> = mutableListOf()

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
