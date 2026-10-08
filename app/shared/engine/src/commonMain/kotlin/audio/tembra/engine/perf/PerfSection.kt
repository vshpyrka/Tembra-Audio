package audio.tembra.engine.perf

/**
 * The named sections a round can measure on both platforms (contracts/tracing-facade.md). Entries carry only their
 * [PerfFamily]; the signpost names live in `perf-catalogue.txt` and reach the code through the generated
 * `signpostName()` (real strings in measurement builds, `""` otherwise). TIMELINE entries are reserved for the
 * timeline feature.
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
}
