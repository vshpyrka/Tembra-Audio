package audio.tembra.engine

/**
 * The only way Kotlin talks to the native engine (Constitution I, II). Implemented by [NativeAudioEngine]
 * on both platforms and by [FakeAudioEngine] in tests. Every call runs on an ordinary thread.
 */
interface AudioEngine : TunerAudio {
    fun describe(): EngineDescriptor
}
