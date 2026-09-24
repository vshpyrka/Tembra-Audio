package audio.tembra.engine

/** The real engine facade: JNI on Android, cinterop on iOS. The only `expect` class besides platform SDK access. */
expect class NativeAudioEngine() : AudioEngine {
    override fun describe(): EngineDescriptor
}
