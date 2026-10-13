package audio.tembra.engine.perf

/**
 * The named sections a round can measure on both platforms (contracts/tracing-facade.md). Entries carry only their
 * [PerfFamily]; the signpost names live in `perf-catalogue.txt` and reach the code through the generated
 * `signpostName()` (real strings in measurement builds, `""` otherwise). The TIMELINE, TRANSPORT and ENGINE_PRIME
 * entries are feature 009's (contracts/perf-instrumentation.md).
 */
enum class PerfSection(val family: PerfFamily) {
    TUNER_LOAD(PerfFamily.TUNER),
    TUNER_FRAME(PerfFamily.TUNER),
    TUNER_MODE_SWITCH(PerfFamily.TUNER),
    METRONOME_LOAD(PerfFamily.METRONOME),
    METRONOME_TICK(PerfFamily.METRONOME),
    METRONOME_TEMPO_CHANGE(PerfFamily.METRONOME),
    TIMELINE_SCROLL(PerfFamily.TIMELINE),
    TIMELINE_ZOOM(PerfFamily.TIMELINE),
    ENGINE_LOAD_CLIP(PerfFamily.ENGINE),
    ENGINE_GRAPH_SWAP(PerfFamily.ENGINE),
    TIMELINE_PROJECT_OPEN(PerfFamily.TIMELINE),
    TIMELINE_WAVEFORM(PerfFamily.TIMELINE),
    TIMELINE_PLAYHEAD_FRAME(PerfFamily.TIMELINE),
    TIMELINE_CLIP_SPLIT(PerfFamily.TIMELINE),
    TIMELINE_PROJECT_SAVE(PerfFamily.TIMELINE),
    TIMELINE_LIBRARY_OPEN(PerfFamily.TIMELINE),
    TRANSPORT_PLAY(PerfFamily.TRANSPORT),
    TRANSPORT_SEEK(PerfFamily.TRANSPORT),
    ENGINE_PRIME(PerfFamily.ENGINE),
}
