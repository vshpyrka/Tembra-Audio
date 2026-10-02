package audio.tembra.ui.platform

/**
 * The platform's document picker for the custom click sound (contracts/click-sounds.md): the Android implementation
 * lives in `:androidApp`, the iOS one in this module's `iosMain`; tests use `FakeSoundFilePicker`. Paths are plain
 * strings so no platform file type reaches common code.
 */
interface SoundFilePicker {
    /** Presents the platform picker for WAV files and copies the chosen file to [destinationPath]. Suspends until the user picks or cancels. */
    suspend fun pickWav(destinationPath: String): SoundPickResult
}
