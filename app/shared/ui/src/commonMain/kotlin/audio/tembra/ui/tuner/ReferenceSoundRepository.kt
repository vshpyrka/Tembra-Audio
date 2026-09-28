package audio.tembra.ui.tuner

import audio.tembra.core.tuner.Instrument
import audio.tembra.core.tuner.Note
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Resolves a string to its reference sound (research R5): `files/tuner/<instrument>-<note>.wav` when bundled, else
 * the synthesized tone at the note's frequency. Each path is read once; hits and misses are both cached.
 */
class ReferenceSoundRepository(private val reader: ResourceReader) {
    private val cache = mutableMapOf<String, ByteArray?>()
    private val mutex = Mutex()

    suspend fun resolve(instrument: Instrument, note: Note): ReferenceSound {
        val name = "${instrument.id}-${note.resourceName}"
        val path = "files/tuner/$name.wav"
        val bytes = mutex.withLock {
            if (cache.containsKey(path)) cache[path] else reader.readOrNull(path).also { cache[path] = it }
        }
        return if (bytes != null) ReferenceSound.Recording(name, bytes) else ReferenceSound.Synth(note.frequencyHz())
    }
}
