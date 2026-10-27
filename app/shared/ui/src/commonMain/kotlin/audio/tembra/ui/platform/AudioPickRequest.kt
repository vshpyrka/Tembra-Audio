package audio.tembra.ui.platform

/** What the picker filters on: the MIME types of the accepted files (advisory; the importer inspects the bytes). */
data class AudioPickRequest(val mimeTypes: List<String>) {
    companion object {
        /** WAV under every MIME type providers use for it (the metronome's custom click sound). */
        val wav = AudioPickRequest(listOf("audio/wav", "audio/x-wav", "audio/vnd.wave", "audio/wave"))

        /** WAV, FLAC and MP3: what the timeline imports (spec FR-044). */
        val timelineAudio = AudioPickRequest(wav.mimeTypes + listOf("audio/flac", "audio/x-flac", "audio/mpeg"))
    }
}
