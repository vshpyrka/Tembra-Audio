package audio.tembra.data.projects

import audio.tembra.core.project.InstrumentLabel

/** One bundled demo loop: its resource name, the track it lands on and its channel count. */
data class DemoLoop(val name: String, val trackName: String, val label: InstrumentLabel, val channels: Int) {
    val fileName: String get() = "$name.wav"
}
