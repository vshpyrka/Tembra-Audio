package audio.tembra.engine

import audio.tembra.engine.cinterop.tembra_engine_describe
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString

@OptIn(ExperimentalForeignApi::class)
actual class NativeAudioEngine actual constructor() : AudioEngine {

    actual override fun describe(): EngineDescriptor {
        val raw = tembra_engine_describe()?.toKString() ?: error("tembra_engine_describe() returned null")
        return EngineDescriptorParser.parse(raw)
    }
}
