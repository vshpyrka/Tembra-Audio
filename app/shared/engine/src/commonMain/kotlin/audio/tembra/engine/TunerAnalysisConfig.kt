package audio.tembra.engine

/** The analyzer's tunables (mirrors `tembra_tuner_config`); the defaults are research R1's values. */
data class TunerAnalysisConfig(
    val windowFrames: Int = 4096,
    val hopFrames: Int = 1024,
    val minHz: Float = 50f,
    val maxHz: Float = 1400f,
    val clarityThreshold: Float = 0.5f,
    val peakRatio: Float = 0.9f,
    val medianSize: Int = 5,
    val watchdogSeconds: Float = 5f,
)
