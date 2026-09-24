package audio.tembra.engine

actual class NativeAudioEngine actual constructor() : AudioEngine {

    actual override fun describe(): EngineDescriptor = EngineDescriptorParser.parse(nativeDescribe())

    private external fun nativeAbiVersion(): Int
    private external fun nativeName(): String
    private external fun nativeVersion(): String
    private external fun nativeDescribe(): String

    private companion object {
        init {
            System.loadLibrary("tembra_engine")
        }
    }
}
