package audio.tembra.ui.platform

import audio.tembra.data.projects.PickedFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.ForwardingSource
import okio.IOException
import okio.Path.Companion.toPath
import okio.Source
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UIKit.UIWindowScene
import platform.UIKit.modalInPresentation
import platform.UIKit.presentationController
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeAudio

/**
 * The Files picker for audio (contracts/project-storage.md, research R11): a `UIDocumentPickerViewController` over the
 * request's types, opened in place (not as a copy) with [allowsMultipleSelection] for [pickMany], presented over
 * whatever the key window currently shows. Each picked URL becomes a [PickedFile] whose opener begins security-scoped
 * access, streams the file through Okio and ends the access when the source is closed, so the importer's copy runs
 * inside the grant and nothing is held afterwards. [present] and [access] are injectable so the simulator tests can
 * answer the picker and observe the access without a real sheet.
 */
class IosAudioFilePicker internal constructor(
    private val present: suspend (types: List<UTType>, multiple: Boolean) -> List<NSURL>?,
    private val access: SecurityScopedAccess,
    private val fileSystem: FileSystem,
    /** Where the sheet is presented: UIKit's main queue; a test passes an unconfined dispatcher, its run loop never spins. */
    private val presentOn: CoroutineDispatcher,
) : AudioFilePicker {

    constructor() : this(present = ::presentPicker, access = SecurityScopedAccess.System, fileSystem = FileSystem.SYSTEM, presentOn = Dispatchers.Main)

    override suspend fun pickOne(request: AudioPickRequest): AudioPickResult = pick(request, multiple = false)

    override suspend fun pickMany(request: AudioPickRequest): AudioPickResult = pick(request, multiple = true)

    private suspend fun pick(request: AudioPickRequest, multiple: Boolean): AudioPickResult {
        val urls = withContext(presentOn) { present(typesOf(request), multiple) } ?: return AudioPickResult.Cancelled
        val files = urls.map { url -> pickedFile(url) }
        return if (files.isEmpty()) AudioPickResult.Cancelled else AudioPickResult.Picked(files)
    }

    /** The [PickedFile] for [url]; opening begins the scoped access, closing the returned source ends it. */
    internal fun pickedFile(url: NSURL): PickedFile {
        val name = url.lastPathComponent?.takeIf { it.isNotBlank() } ?: FALLBACK_NAME
        return PickedFile(name) { openScoped(url) }
    }

    private fun openScoped(url: NSURL): Source {
        val path = url.path ?: throw IOException("the picked URL has no file path")
        val granted = access.begin(url)
        val source = try {
            fileSystem.source(path.toPath())
        } catch (failure: IOException) {
            if (granted) access.end(url)
            throw failure
        }
        return object : ForwardingSource(source) {
            override fun close() {
                try {
                    super.close()
                } finally {
                    if (granted) access.end(url)
                }
            }
        }
    }

    internal companion object {
        /** The label of a picked file whose name is empty. */
        internal const val FALLBACK_NAME = "Audio file"

        /** The request's MIME types as uniform types; an unknown one falls back to every audio type. */
        internal fun typesOf(request: AudioPickRequest): List<UTType> {
            val types = request.mimeTypes.mapNotNull { mime -> UTType.typeWithMIMEType(mime) }.distinct()
            return types.ifEmpty { listOfNotNull(UTTypeAudio) }
        }

        /** Presents the picker and suspends until the user picks (the URLs) or cancels (null); dismisses it if cancelled. */
        private suspend fun presentPicker(types: List<UTType>, multiple: Boolean): List<NSURL>? {
            val presenter = topViewController() ?: return null
            val delegate = IosDocumentPickerDelegate()
            val picker = UIDocumentPickerViewController(forOpeningContentTypes = types, asCopy = false)
            picker.delegate = delegate
            picker.allowsMultipleSelection = multiple
            // Only the explicit Cancel dismisses the sheet; a dismissal that still happens answers as cancelled.
            picker.modalInPresentation = true
            picker.presentationController?.delegate = delegate
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
    }
}
