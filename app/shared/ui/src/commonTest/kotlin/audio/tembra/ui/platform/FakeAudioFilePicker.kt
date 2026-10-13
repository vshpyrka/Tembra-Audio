package audio.tembra.ui.platform

import audio.tembra.data.projects.PickedFile
import okio.Buffer

/**
 * Scripted picker for ViewModel tests: every pick records its request in [requests] and answers [next]; [picked]
 * builds a result whose file opens the given bytes, as a platform grant would.
 */
class FakeAudioFilePicker(var next: AudioPickResult = AudioPickResult.Cancelled) : AudioFilePicker {
    val requests: MutableList<AudioPickRequest> = mutableListOf()

    override suspend fun pickOne(request: AudioPickRequest): AudioPickResult {
        requests += request
        return next
    }

    override suspend fun pickMany(request: AudioPickRequest): AudioPickResult {
        requests += request
        return next
    }

    companion object {
        /** One picked file named [displayName] whose bytes are [bytes]. */
        fun picked(displayName: String, bytes: ByteArray = ByteArray(0)): AudioPickResult.Picked =
            picked(displayName to bytes)

        /** Several picked files in order. */
        fun picked(vararg files: Pair<String, ByteArray>): AudioPickResult.Picked =
            AudioPickResult.Picked(files.map { (name, bytes) -> PickedFile(name) { Buffer().write(bytes) } })
    }
}
