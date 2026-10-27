package audio.tembra.ui.timeline

import audio.tembra.core.project.AudioAssetId
import audio.tembra.core.project.Beats
import audio.tembra.core.project.ClipId
import audio.tembra.data.projects.OverviewStatus
import audio.tembra.data.projects.WaveformLevel
import audio.tembra.data.projects.WaveformOverview

/** Overviews and clips for the lane and screen tests: one base level whose every bucket peaks at [peak]. */
object FakeOverviews {
    const val SAMPLE_RATE = 48_000

    fun overview(frames: Long, peak: Int = 100, channels: Int = 1): WaveformOverview {
        val buckets = ((frames + WaveformOverview.BASE_BUCKET - 1) / WaveformOverview.BASE_BUCKET).toInt()
        val data = ByteArray(buckets * channels * 2)
        for (bucket in 0 until buckets) {
            for (channel in 0 until channels) {
                val index = (bucket * channels + channel) * 2
                data[index] = (-peak).toByte()
                data[index + 1] = peak.toByte()
            }
        }
        val level = WaveformLevel(WaveformOverview.BASE_BUCKET, buckets, buckets, channels, data)
        return WaveformOverview(channels, frames, buckets.toLong(), listOf(level))
    }

    /** A clip of [lengthBeats] at 124 BPM: the frames follow from the tempo so the drawn span and the audio agree. */
    fun clip(
        id: String,
        name: String,
        startBeats: Double,
        lengthBeats: Double,
        available: Boolean = true,
        status: OverviewStatus = OverviewStatus.Ready,
        withOverview: Boolean = true,
    ): ClipUi {
        val frames = (lengthBeats * 60.0 / 124.0 * SAMPLE_RATE).toLong()
        return ClipUi(
            id = ClipId(id),
            assetId = AudioAssetId(id.padEnd(64, 'a')),
            name = name,
            startBeats = Beats(startBeats),
            lengthBeats = Beats(lengthBeats),
            offsetFrames = 0,
            lengthFrames = frames,
            sampleRate = SAMPLE_RATE,
            overview = if (withOverview && available) overview(frames) else null,
            overviewStatus = status,
            available = available,
        )
    }
}
