package audio.tembra.ui.drop

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import audio.tembra.data.projects.PickedFile
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSPropertyListSerialization
import platform.Foundation.NSItemProvider
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeAudio
import platform.UniformTypeIdentifiers.UTTypeFolder
import platform.UniformTypeIdentifiers.conformsToType
import kotlin.coroutines.resume

/**
 * Files dropped onto the app on iPad (feature 011, spec FR-026; research R12): the drop session's item providers are
 * taken while the drop is valid, and each item's file representation is copied into [cacheDirectory] inside the
 * provider's callback (the system removes its own copy once the callback returns). A folder gives its direct children:
 * audio files, other files by name, and the folders inside it, named and not entered.
 */
class IosDroppedFilesReader(private val cacheDirectory: String) : DroppedFilesReader {

    override fun accepts(event: DragAndDropEvent): Boolean = providersOf(event).any { it.isAudio() || it.isFolder() }

    override fun audioCount(event: DragAndDropEvent): Int = providersOf(event).count { it.isAudio() }

    override suspend fun read(event: DragAndDropEvent): List<DroppedItem> {
        val providers = providersOf(event)
        return providers.map { itemOf(it) }
    }

    internal suspend fun itemOf(provider: NSItemProvider): DroppedItem = when {
        provider.isFolder() -> copy(provider, UTTypeFolder)?.let(::folderAt) ?: DroppedItem.Folder(nameOf(provider), contents = null)
        provider.isAudio() -> copy(provider, UTTypeAudio)?.let { path -> DroppedItem.AudioFile(picked(path)) } ?: DroppedItem.Other(nameOf(provider))
        // A provider whose types the system does not resolve, but which names a file: judged by the copy's name.
        provider.hasItemConformingToTypeIdentifier(FILE_URL) -> copyFromFileUrl(provider)?.let(::itemAt) ?: DroppedItem.Other(nameOf(provider))
        else -> DroppedItem.Other(nameOf(provider))
    }

    /** A copied file or folder, judged by what it is and its name. */
    private fun itemAt(path: String): DroppedItem {
        val name = path.toPath().name
        return when {
            FileSystem.SYSTEM.metadataOrNull(path.toPath())?.isDirectory == true -> folderAt(path)
            isAudioFileName(name) -> DroppedItem.AudioFile(picked(path))
            else -> DroppedItem.Other(name)
        }
    }

    private fun folderAt(path: String): DroppedItem.Folder {
        val name = path.toPath().name
        val children = FileSystem.SYSTEM.listOrNull(path.toPath()) ?: return DroppedItem.Folder(name, contents = null)
        val contents = children.filterNot { it.name.startsWith(".") }.map { child ->
            when {
                FileSystem.SYSTEM.metadataOrNull(child)?.isDirectory == true -> DroppedItem.Folder(child.name, contents = emptyList())
                isAudioFileName(child.name) -> DroppedItem.AudioFile(picked(child.toString()))
                else -> DroppedItem.Other(child.name)
            }
        }
        return DroppedItem.Folder(name, contents)
    }

    /** The provider's file representation of its first type conforming to [type], copied into the cache; null on failure. */
    private suspend fun copy(provider: NSItemProvider, type: UTType): String? {
        val identifier = provider.registeredTypeIdentifiers.map { it as String }.firstOrNull { UTType.typeWithIdentifier(it)?.conformsToType(type) == true }
            ?: return null
        return suspendCancellableCoroutine { continuation ->
            provider.loadFileRepresentationForTypeIdentifier(identifier) { url, _ ->
                continuation.resume(url?.let { copyIntoCache(it, nameFor(provider, it)) })
            }
        }
    }

    /** The provider's file URL item, copied into the cache; null when it names none or the copy fails. */
    @OptIn(ExperimentalForeignApi::class)
    private suspend fun copyFromFileUrl(provider: NSItemProvider): String? = suspendCancellableCoroutine { continuation ->
        provider.loadItemForTypeIdentifier(FILE_URL, options = null) { item, _ ->
            // The item is the URL, or its data as the provider encodes one (a file URL's provider in the test binary).
            val url = item as? NSURL ?: (item as? NSData)?.let(::urlOf)
            continuation.resume(url?.let { copyIntoCache(it, it.lastPathComponent) })
        }
    }

    /** A URL item's data: a property list whose first entry is the URL's string. */
    @OptIn(ExperimentalForeignApi::class)
    private fun urlOf(data: NSData): NSURL? {
        val list = NSPropertyListSerialization.propertyListWithData(data, options = 0u, format = null, error = null) as? List<*>
        return (list?.firstOrNull() as? String)?.let { NSURL.URLWithString(it) }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun copyIntoCache(url: NSURL, preferredName: String?): String? {
        val name = preferredName?.takeIf { it.isNotBlank() && '/' !in it } ?: url.lastPathComponent?.takeIf { it.isNotBlank() } ?: return null
        val directory = "$cacheDirectory/drops/${NSUUID().UUIDString}"
        FileSystem.SYSTEM.createDirectories(directory.toPath())
        val destination = "$directory/$name"
        val copied = NSFileManager.defaultManager.copyItemAtURL(url, toURL = NSURL.fileURLWithPath(destination), error = null)
        return if (copied) destination else null
    }

    private fun picked(path: String): PickedFile = PickedFile(path.toPath().name) { FileSystem.SYSTEM.source(path.toPath()) }

    /**
     * The name a copied representation goes by: the provider's suggested name (a representation's temporary file can be
     * named generically), with the copy's extension when the suggestion has none; the copy's own name otherwise.
     */
    private fun nameFor(provider: NSItemProvider, copy: NSURL): String? {
        val suggested = provider.suggestedName?.takeIf { it.isNotBlank() } ?: return copy.lastPathComponent
        val extension = copy.pathExtension?.takeIf { it.isNotEmpty() }
        return if ('.' in suggested || extension == null) suggested else "$suggested.$extension"
    }

    /** The provider's suggested name, with its type's extension when the name has none. */
    private fun nameOf(provider: NSItemProvider): String {
        val suggested = provider.suggestedName?.takeIf { it.isNotBlank() } ?: return FALLBACK_NAME
        if ('.' in suggested) return suggested
        val extension = provider.registeredTypeIdentifiers.firstOrNull()?.let { UTType.typeWithIdentifier(it as String)?.preferredFilenameExtension }
        return if (extension == null) suggested else "$suggested.$extension"
    }

    // Compose's iOS drop event hands the session's items through an experimental member (Compose Multiplatform 1.12).
    @OptIn(ExperimentalComposeUiApi::class)
    private fun providersOf(event: DragAndDropEvent): List<NSItemProvider> = event.items.map { it.itemProvider }

    private fun NSItemProvider.isAudio(): Boolean = hasItemConformingToTypeIdentifier(UTTypeAudio.identifier)

    private fun NSItemProvider.isFolder(): Boolean = hasItemConformingToTypeIdentifier(UTTypeFolder.identifier)

    private companion object {
        const val FALLBACK_NAME = "Dropped item"
        const val FILE_URL = "public.file-url"
    }
}

/**
 * Whether a file named [name] is audio by its extension: its uniform type where the system resolves one, otherwise the
 * common audio extensions (a process without the system's type database, such as the test binary, resolves none).
 */
internal fun isAudioFileName(name: String): Boolean {
    val extension = name.substringAfterLast('.', missingDelimiterValue = "").lowercase().takeIf { it.isNotEmpty() } ?: return false
    val type = UTType.typeWithFilenameExtension(extension)
    return if (type != null && !type.dynamic) type.conformsToType(UTTypeAudio) else extension in AudioExtensions
}

private val AudioExtensions = setOf("wav", "wave", "flac", "mp3", "aif", "aiff", "aifc", "m4a", "caf", "aac")
