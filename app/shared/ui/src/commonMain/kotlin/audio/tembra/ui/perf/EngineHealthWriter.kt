package audio.tembra.ui.perf

/** Writes `engine-health.json` where the pipeline pulls it from and returns the path (feature 007). */
fun interface EngineHealthWriter {
    fun write(json: String): String
}
