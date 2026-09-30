package audio.tembra.ui.platform

/**
 * Scripted picker for ViewModel tests (contracts/click-sounds.md): every [pickWav] records its destination in
 * [destinations] and returns [next]; when [next] is [SoundPickResult.Picked] it first writes [bytesToWrite] to the
 * destination in [files], which a test shares with `FakeCustomClickSoundStore.files` so the store can commit it. When
 * [next] is [SoundPickResult.Failed] and [partialOnFailure] is set, it writes that partial copy first, as a platform
 * picker cut short mid-copy can.
 */
class FakeSoundFilePicker(
    var next: SoundPickResult = SoundPickResult.Cancelled,
    var bytesToWrite: ByteArray = ByteArray(0),
    val files: MutableMap<String, ByteArray> = mutableMapOf(),
    var partialOnFailure: ByteArray? = null,
) : SoundFilePicker {
    val destinations: MutableList<String> = mutableListOf()

    override suspend fun pickWav(destinationPath: String): SoundPickResult {
        destinations += destinationPath
        val result = next
        val written = when (result) {
            is SoundPickResult.Picked -> bytesToWrite
            is SoundPickResult.Failed -> partialOnFailure
            SoundPickResult.Cancelled -> null
        }
        if (written != null) files[destinationPath] = written.copyOf()
        return result
    }
}
