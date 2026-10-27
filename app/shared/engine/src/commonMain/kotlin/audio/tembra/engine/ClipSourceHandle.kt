package audio.tembra.engine

import kotlin.jvm.JvmInline

/** A decoder the engine streams from, open until [TimelineAudio.closeClipSource]; the C ABI's `int32_t` handle. */
@JvmInline
value class ClipSourceHandle(val id: Int)
