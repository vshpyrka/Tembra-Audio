package audio.tembra.data.projects

import okio.Source

/**
 * The bundled demo loops (`files/demo/<name>.wav` Compose resources) as Okio sources, bound in `:shared:ui` so this
 * module never depends on Compose (contracts/project-storage.md). [name] is `drums`, `bass`, `keys` or `pad`.
 */
interface DemoAudioSource {
    suspend fun open(name: String): Source
}
