package audio.tembra.ui.platform

import kotlinx.coroutines.CompletableDeferred
import platform.Foundation.NSURL
import platform.UIKit.UIAdaptivePresentationControllerDelegateProtocol
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIPresentationController
import platform.darwin.NSObject

/**
 * Answers one document picker: the picked files' URLs in picker order, or null when the user cancels, including a
 * dismissal of the sheet that bypasses `documentPickerWasCancelled` (it is also the picker's presentation controller
 * delegate). The picker holds its delegates weakly; a caller suspended in [await] keeps this object reachable until
 * the answer arrives.
 */
internal class IosDocumentPickerDelegate :
    NSObject(),
    UIDocumentPickerDelegateProtocol,
    UIAdaptivePresentationControllerDelegateProtocol {

    private val result = CompletableDeferred<List<NSURL>?>()

    /** True once the user picked or cancelled. */
    val isAnswered: Boolean get() = result.isCompleted

    /** Suspends until the user picks (the URLs) or cancels (null). */
    suspend fun await(): List<NSURL>? = result.await()

    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        result.complete(didPickDocumentsAtURLs.filterIsInstance<NSURL>().ifEmpty { null })
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        result.complete(null)
    }

    override fun presentationControllerDidDismiss(presentationController: UIPresentationController) {
        result.complete(null)
    }
}
