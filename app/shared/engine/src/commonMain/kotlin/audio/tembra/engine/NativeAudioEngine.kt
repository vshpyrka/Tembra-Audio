package audio.tembra.engine

/**
 * The real engine facade: JNI on Android, cinterop on iOS. The only `expect` class besides platform SDK access.
 * There is no common constructor (research R15): the Android actual takes the application `Context` for transient
 * audio focus around tones, the iOS actual takes nothing and configures the `AVAudioSession` itself. Every
 * [AudioEngine] member is implemented by the actuals.
 */
expect class NativeAudioEngine : AudioEngine
