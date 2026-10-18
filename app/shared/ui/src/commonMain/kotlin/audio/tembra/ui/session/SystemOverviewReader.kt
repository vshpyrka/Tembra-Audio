package audio.tembra.ui.session

import audio.tembra.data.projects.WaveformOverview
import audio.tembra.data.projects.WaveformOverviewReader
import okio.FileSystem
import okio.Path.Companion.toPath

/** The production reader: the device file system through the data layer's parser. */
object SystemOverviewReader : OverviewReader {
    override fun read(path: String): WaveformOverview? = WaveformOverviewReader.read(FileSystem.SYSTEM, path.toPath())
}
