package audio.tembra.data

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AppDirectoriesTest {
    private val directories: AppDirectories =
        AndroidAppDirectories(InstrumentationRegistry.getInstrumentation().targetContext)

    @Test
    fun documentsIsAnAbsoluteExistingDirectory() {
        assertTrue(directories.documents.startsWith("/"), directories.documents)
        assertTrue(File(directories.documents).isDirectory, directories.documents)
    }

    @Test
    fun cacheIsAnAbsoluteExistingDirectoryDistinctFromDocuments() {
        assertTrue(directories.cache.startsWith("/"), directories.cache)
        assertTrue(File(directories.cache).isDirectory, directories.cache)
        assertNotEquals(directories.documents, directories.cache)
    }

    @Test
    fun preferencesIsAWritableDatastoreDirectory() {
        assertTrue(directories.preferences.startsWith("/"), directories.preferences)
        assertTrue(directories.preferences.endsWith("/datastore"), directories.preferences)
        val directory = File(directories.preferences)
        assertTrue(directory.isDirectory, directories.preferences)
        assertTrue(directory.canWrite(), directories.preferences)
        assertNotEquals(directories.documents, directories.preferences)
        assertNotEquals(directories.cache, directories.preferences)
    }
}
