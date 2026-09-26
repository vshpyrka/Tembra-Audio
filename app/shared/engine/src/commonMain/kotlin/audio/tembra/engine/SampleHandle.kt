package audio.tembra.engine

import kotlin.jvm.JvmInline

/** A decoded recording held by the engine until [TunerAudio.unloadSample]. */
@JvmInline
value class SampleHandle(val id: Int)
