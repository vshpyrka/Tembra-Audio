package audio.tembra.data.projects

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The committed golden fixture `specs/009-timeline-arranger/fixtures/demo-project.json` is byte-identical to the
 * codec's output (contracts/project-document.md, tests). JVM only because it reads the repository file; it finds the
 * repository root by walking up from the module directory.
 */
class DemoProjectGoldenFileTest {

    @Test
    fun theFixtureMatchesTheCodecOutput() {
        var directory: File? = File(System.getProperty("user.dir")).absoluteFile
        while (directory != null && !File(directory, "specs/009-timeline-arranger").isDirectory) directory = directory.parentFile
        val root = assertNotNull(directory, "repository root not found above ${System.getProperty("user.dir")}")
        val fixture = File(root, "specs/009-timeline-arranger/fixtures/demo-project.json").readText()
        assertEquals(DemoProjectGolden.TEXT, fixture)
    }
}
