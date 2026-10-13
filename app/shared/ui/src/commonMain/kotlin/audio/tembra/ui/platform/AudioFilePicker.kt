package audio.tembra.ui.platform

/**
 * The platform's document picker for audio files (contracts/project-storage.md, research R11), generalised from the
 * metronome's WAV picker: the Android implementation lives in `:androidApp` (`OpenDocument` / `OpenMultipleDocuments`
 * through `AudioFilePickerHost`), the iOS one in this module's `iosMain` (`UIDocumentPickerViewController`); tests
 * use `FakeAudioFilePicker`. A pick hands back readable content handles, never platform file types: the importer copies
 * them into the project with Okio while the platform grant is still valid.
 */
interface AudioFilePicker {
    /** Presents the picker for one file; suspends until the user picks ([AudioPickResult.Picked] with one file) or cancels. */
    suspend fun pickOne(request: AudioPickRequest): AudioPickResult

    /** Presents the picker for several files; [AudioPickResult.Picked] lists them in picker order. */
    suspend fun pickMany(request: AudioPickRequest): AudioPickResult
}
