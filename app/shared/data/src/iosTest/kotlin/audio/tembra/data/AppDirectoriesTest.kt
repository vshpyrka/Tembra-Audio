package audio.tembra.data

import platform.Foundation.NSFileManager
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AppDirectoriesTest {
    private val directories: AppDirectories = IosAppDirectories()

    @Test
    fun documentsIsAnAbsoluteExistingDirectory() {
        assertTrue(directories.documents.startsWith("/"), directories.documents)
        assertTrue(NSFileManager.defaultManager.fileExistsAtPath(directories.documents), directories.documents)
    }

    @Test
    fun cacheIsAnAbsoluteExistingDirectoryDistinctFromDocuments() {
        assertTrue(directories.cache.startsWith("/"), directories.cache)
        assertTrue(NSFileManager.defaultManager.fileExistsAtPath(directories.cache), directories.cache)
        assertNotEquals(directories.documents, directories.cache)
    }
}
