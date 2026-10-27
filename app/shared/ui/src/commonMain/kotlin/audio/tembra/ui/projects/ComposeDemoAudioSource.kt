package audio.tembra.ui.projects

import audio.tembra.data.projects.DemoAudioSource
import audio.tembra.ui.resources.Res
import okio.Buffer
import okio.Source

/** The demo's four loops from the bundled Compose resources (`files/demo/<name>.wav`; research R18). */
object ComposeDemoAudioSource : DemoAudioSource {
    override suspend fun open(name: String): Source = Buffer().write(Res.readBytes("files/demo/$name.wav"))
}
