package audio.tembra.core.version

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SemanticVersionTest {

    @Test
    fun parsesThreeDotSeparatedComponents() {
        assertEquals(SemanticVersion(0, 1, 0), SemanticVersion.parse("0.1.0"))
        assertEquals(SemanticVersion(12, 34, 56), SemanticVersion.parse("12.34.56"))
    }

    @Test
    fun toStringRoundTrips() {
        assertEquals("1.2.3", SemanticVersion.parse("1.2.3").toString())
        assertEquals("0.1.0", SemanticVersion(0, 1, 0).toString())
    }

    @Test
    fun ordersByMajorThenMinorThenPatch() {
        val sorted = listOf("1.0.1", "0.9.9", "1.1.0", "1.0.0").map(SemanticVersion::parse).sorted()
        assertEquals(listOf("0.9.9", "1.0.0", "1.0.1", "1.1.0"), sorted.map { it.toString() })
        assertTrue(SemanticVersion(0, 9, 9) < SemanticVersion(1, 0, 0))
    }

    @Test
    fun rejectsMalformedInput() {
        for (bad in listOf("", "1.2", "1.2.3.4", "a.b.c", "-1.0.0", "1.2.x", " 1.2.3", "1..3")) {
            assertFailsWith<IllegalArgumentException>("parse(\"$bad\") must throw") { SemanticVersion.parse(bad) }
            assertNull(SemanticVersion.parseOrNull(bad), "parseOrNull(\"$bad\") must be null")
        }
    }

    @Test
    fun rejectsNegativeComponents() {
        assertFailsWith<IllegalArgumentException> { SemanticVersion(-1, 0, 0) }
        assertFailsWith<IllegalArgumentException> { SemanticVersion(0, -1, 0) }
        assertFailsWith<IllegalArgumentException> { SemanticVersion(0, 0, -1) }
    }
}
