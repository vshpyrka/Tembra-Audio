package audio.tembra.ui.platform

import kotlinx.coroutines.CompletableDeferred
import platform.Foundation.NSURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.darwin.NSObject

/**
 * Answers one document picker: the picked file's URL, or null when the user cancels. The picker holds its delegate
 * weakly; a caller suspended in [await] keeps this object reachable until the answer arrives.
 */
internal class IosDocumentPickerDelegate :
    NSObject(),
    UIDocumentPickerDelegateProtocol {

    private val result = CompletableDeferred<NSURL?>()

    /** True once the user picked or cancelled. */
    val isAnswered: Boolean get() = result.isCompleted

    /** Suspends until the user picks (the URL) or cancels (null). */
    suspend fun await(): NSURL? = result.await()

    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        result.complete(didPickDocumentsAtURLs.firstOrNull() as? NSURL)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        result.complete(null)
    }
}
