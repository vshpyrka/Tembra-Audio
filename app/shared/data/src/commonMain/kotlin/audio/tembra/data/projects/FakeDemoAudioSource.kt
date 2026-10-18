package audio.tembra.data.projects

import okio.Buffer
import okio.IOException
import okio.Source

/** In-memory demo loops for tests, beside the interface so `:shared:ui` tests can use it too. */
class FakeDemoAudioSource(private val files: Map<String, ByteArray>) : DemoAudioSource {
    override suspend fun open(name: String): Source {
        val bytes = files[name] ?: throw IOException("No demo loop named \"$name\"")
        return Buffer().write(bytes)
    }
}
