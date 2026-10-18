package audio.tembra.ui.session

import audio.tembra.data.projects.WaveformOverview

/** Reads a `.peaks` overview from its path; null while the file is absent or unreadable. Tests script it. */
fun interface OverviewReader {
    fun read(path: String): WaveformOverview?
}
