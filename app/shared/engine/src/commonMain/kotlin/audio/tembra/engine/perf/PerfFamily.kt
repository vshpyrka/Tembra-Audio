package audio.tembra.engine.perf

/**
 * The families of the section catalogue (spec FR-012); startup has none, the platforms' launch metrics measure it.
 * TRANSPORT (feature 009) is the project transport's own play and seek paths.
 */
enum class PerfFamily { TUNER, METRONOME, TIMELINE, TRANSPORT, ENGINE }
