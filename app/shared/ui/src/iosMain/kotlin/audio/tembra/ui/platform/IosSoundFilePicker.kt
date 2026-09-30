package audio.tembra.ui.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UIKit.UIWindowScene
import platform.UniformTypeIdentifiers.UTTypeWAV

/**
 * The Files picker for the custom click sound (contracts/click-sounds.md): a `UIDocumentPickerViewController` for WAV
 * files opened as a copy, presented over whatever the key window currently shows. The picked copy lands in the app's
 * temporary inbox and is copied to the destination with a 64 MB cap.
 */
class IosSoundFilePicker : SoundFilePicker {

    override suspend fun pickWav(destinationPath: String): SoundPickResult {
        val url = withContext(Dispatchers.Main) { present() } ?: return SoundPickResult.Cancelled
        return withContext(Dispatchers.IO) { copyPicked(url, destinationPath) }
    }

    /** Presents the picker and suspends until the user picks (the URL) or cancels (null); dismisses it if cancelled. */
    private suspend fun present(): NSURL? {
        val presenter = topViewController() ?: return null
        val delegate = IosDocumentPickerDelegate()
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOfNotNull(UTTypeWAV), asCopy = true)
        picker.delegate = delegate
        picker.allowsMultipleSelection = false
        presenter.presentViewController(picker, animated = true, completion = null)
        try {
            return delegate.await()
        } finally {
            if (!delegate.isAnswered) picker.dismissViewControllerAnimated(true, completion = null)
        }
    }

    /** The key window's root controller, or the controller it is currently presenting on top. */
    private fun topViewController(): UIViewController? {
        val window = UIApplication.sharedApplication.connectedScenes
            .filterIsInstance<UIWindowScene>()
            .firstNotNullOfOrNull { it.keyWindow }
        var controller = window?.rootViewController ?: return null
        while (true) controller = controller.presentedViewController ?: return controller
    }

    internal companion object {
        /** The largest custom sound accepted: 64 MB. */
        private const val MAX_BYTES = 64L * 1024 * 1024

        /**
         * Copies the file at [url] to [destinationPath], replacing what is there. Refuses a file over 64 MB; a failed
         * copy leaves nothing at the destination. Returns the file's name without its extension.
         */
        @OptIn(ExperimentalForeignApi::class)
        internal fun copyPicked(url: NSURL, destinationPath: String): SoundPickResult {
            val files = NSFileManager.defaultManager
            val sourcePath = url.path ?: return SoundPickResult.Failed(SoundPickFailure.Unreadable)
            val size = (files.attributesOfItemAtPath(sourcePath, error = null)?.get(NSFileSize) as? NSNumber)?.longLongValue
                ?: return SoundPickResult.Failed(SoundPickFailure.Unreadable)
            if (size > MAX_BYTES) return SoundPickResult.Failed(SoundPickFailure.TooLarge)
            val parent = destinationPath.substringBeforeLast('/', missingDelimiterValue = "")
            if (parent.isNotEmpty()) {
                files.createDirectoryAtPath(parent, withIntermediateDirectories = true, attributes = null, error = null)
            }
            files.removeItemAtPath(destinationPath, error = null)
            if (!files.copyItemAtPath(sourcePath, toPath = destinationPath, error = null)) {
                files.removeItemAtPath(destinationPath, error = null)
                return SoundPickResult.Failed(SoundPickFailure.Unreadable)
            }
            val name = url.URLByDeletingPathExtension?.lastPathComponent ?: url.lastPathComponent.orEmpty()
            return SoundPickResult.Picked(name)
        }
    }
}
