package audio.tembra.data.projects

import audio.tembra.engine.TimelineAudio

/**
 * The free-space probe before every `recordStart` (contracts/take-storage.md "Free space"; spec FR-025): the bytes
 * free on the volume holding a project's `audio/` folder, through the engine's `statvfs`. Below [RECORD_MIN_FREE_BYTES]
 * the controller refuses to start; an unknown answer (-1) does not refuse, the engine itself ends a take at its own
 * 50 MB floor.
 */
class TakeFreeSpace(private val audio: TimelineAudio) {

    /** Free bytes on the volume holding [path]; -1 when unknown. */
    fun freeBytes(path: String): Long = audio.freeBytes(path)

    /** False when the volume holding [path] has fewer than [RECORD_MIN_FREE_BYTES] free. */
    fun canRecord(path: String): Boolean {
        val free = freeBytes(path)
        return free < 0 || free >= RECORD_MIN_FREE_BYTES
    }

    companion object {
        /** 200 MB: about 23 minutes of mono 24-bit audio at 48 kHz. */
        const val RECORD_MIN_FREE_BYTES: Long = 200L * 1024 * 1024
    }
}
