package audio.tembra.data.projects

import okio.Source

/**
 * A file the platform picker chose (contracts/project-storage.md): its display name and a way to open its bytes
 * while the platform's grant lasts. The picker in `:shared:ui` produces it; the importer consumes it once.
 */
class PickedFile(val displayName: String, private val opener: suspend () -> Source) {
    suspend fun open(): Source = opener()
}
