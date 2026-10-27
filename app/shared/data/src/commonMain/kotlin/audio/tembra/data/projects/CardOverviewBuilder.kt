package audio.tembra.data.projects

import audio.tembra.core.project.AudioAssetId
import audio.tembra.core.project.Beats
import audio.tembra.core.project.Project
import audio.tembra.core.project.clipEndBeats
import audio.tembra.core.project.lengthBeats
import audio.tembra.core.project.tempoMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Derives the card overview of spec FR-054 at save time: the project length cut into [SPANS] equal spans; in each,
 * every unmuted track's peak is the loudest of its clips' overview buckets inside the span times the track gain,
 * and the span takes the loudest track's peak and colour. Assets without an overview count as silence; a project
 * without clips gives [CardOverview.Empty].
 */
object CardOverviewBuilder {
    const val SPANS = 48

    fun build(project: Project, overviews: Map<AudioAssetId, WaveformOverview>, spans: Int = SPANS): CardOverview {
        val length = project.lengthBeats.value
        if (length <= 0.0 || spans <= 0) return CardOverview.Empty
        val tempoMap = project.tempoMap
        val peaks = ByteArray(spans)
        val colors = ByteArray(spans)
        for (span in 0 until spans) {
            val spanStart = length * span / spans
            val spanEnd = length * (span + 1) / spans
            var loudest = 0f
            var loudestColor = 0
            for (track in project.tracks) {
                if (track.muted) continue
                val gain = track.gainDb.toLinear()
                var trackPeak = 0f
                for (clip in track.clips) {
                    val asset = project.assets[clip.assetId] ?: continue
                    val overview = overviews[clip.assetId] ?: continue
                    val clipStart = clip.startBeats.value
                    val from = max(spanStart, clipStart)
                    val to = min(spanEnd, project.clipEndBeats(clip).value)
                    if (to <= from) continue
                    val firstFrame = clip.offsetFrames + tempoMap.beatsToFrames(Beats(from - clipStart), asset.sampleRate)
                    val lastFrame = clip.offsetFrames + tempoMap.beatsToFrames(Beats(to - clipStart), asset.sampleRate)
                    val peak = overview.peakBetween(firstFrame, max(lastFrame, firstFrame + 1)) / 127f * gain
                    trackPeak = max(trackPeak, peak)
                }
                if (trackPeak > loudest) {
                    loudest = trackPeak
                    loudestColor = track.colorIndex
                }
            }
            peaks[span] = (loudest * 255f).roundToInt().coerceIn(0, 255).toByte()
            colors[span] = loudestColor.toByte()
        }
        return CardOverview(spans, peaks, colors)
    }
}
