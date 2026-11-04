package audio.tembra.engine

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/**
 * Where the engine's slow control calls run (feature 010, T100–T103): the session switches, the audio unit and stream
 * opens, closes and restarts, which take tens to hundreds of milliseconds on the platform and must never hold up the
 * main thread that asks for them. One serial dispatcher for the whole app, so every such call keeps the order it was
 * asked in: a transport close launched as the Timeline leaves precedes the tuner's or the metronome's start on the
 * next screen, and a disarm precedes the arm launched after it. The actuals and the view models take it as their
 * default and tests inject their own.
 */
object EngineDispatchers {
    val blocking: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)
}
