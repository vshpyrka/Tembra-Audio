package audio.tembra.ui.platform

/**
 * Scripted picker for ViewModel tests (contracts/click-sounds.md): every [pickWav] records its destination in
 * [destinations] and returns [next]; when [next] is [SoundPickResult.Picked] it first writes [bytesToWrite] to the
 * destination in [files], which a test shares with `FakeCustomClickSoundStore.files` so the store can commit it.
 */
class FakeSoundFilePicker(
    var next: SoundPickResult = SoundPickResult.Cancelled,
    var bytesToWrite: ByteArray = ByteArray(0),
    val files: MutableMap<String, ByteArray> = mutableMapOf(),
) : SoundFilePicker {
    val destinations: MutableList<String> = mutableListOf()

    override suspend fun pickWav(destinationPath: String): SoundPickResult {
        destinations += destinationPath
        val result = next
        if (result is SoundPickResult.Picked) files[destinationPath] = bytesToWrite.copyOf()
        return result
    }
}
