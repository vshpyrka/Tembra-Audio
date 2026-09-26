package audio.tembra.ui.tuner

/** Reads a bundled resource by path, or null when it does not exist; the seam that keeps the repository testable. */
fun interface ResourceReader {
    suspend fun readOrNull(path: String): ByteArray?
}
