package audio.tembra.core.project

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `countIn` on the wire (feature 010, data-model.md section 1): the enum name, an unknown name read as `ONE_BAR`, and a
 * feature 009 document without the field read as `ONE_BAR`. The `Json` mirrors the codec's reading rules.
 */
class CountInSerializerTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val featureNineDocument = """{"id":"p","name":"Song","tempo":120,"timeSignature":"4/4","assets":{},"tracks":[]}"""

    @Test
    fun writesTheEnumName() {
        assertEquals("\"OFF\"", json.encodeToString(CountInSerializer, CountIn.OFF))
        assertEquals("\"ONE_BAR\"", json.encodeToString(CountInSerializer, CountIn.ONE_BAR))
        assertEquals("\"TWO_BARS\"", json.encodeToString(CountInSerializer, CountIn.TWO_BARS))
    }

    @Test
    fun readsEveryKnownNameAndAnUnknownOneAsOneBar() {
        assertEquals(CountIn.OFF, json.decodeFromString(CountInSerializer, "\"OFF\""))
        assertEquals(CountIn.TWO_BARS, json.decodeFromString(CountInSerializer, "\"TWO_BARS\""))
        assertEquals(CountIn.ONE_BAR, json.decodeFromString(CountInSerializer, "\"FIVE_BARS\""))
        assertEquals(CountIn.ONE_BAR, json.decodeFromString(CountInSerializer, "\"\""))
    }

    @Test
    fun aProjectWritesCountInLastAfterTracks() {
        val text = json.encodeToString(Project.serializer(), ProjectFixtures.empty.copy(countIn = CountIn.TWO_BARS))
        assertTrue(text.endsWith("\"tracks\":[],\"countIn\":\"TWO_BARS\"}"), text)
    }

    @Test
    fun aFeatureNineDocumentWithoutTheFieldReadsAsOneBar() {
        val project = json.decodeFromString(Project.serializer(), featureNineDocument)
        assertEquals(CountIn.ONE_BAR, project.countIn)
    }

    @Test
    fun aDocumentWithAnUnknownCountInReadsAsOneBar() {
        val text = featureNineDocument.dropLast(1) + ",\"countIn\":\"THREE_BARS\"}"
        assertEquals(CountIn.ONE_BAR, json.decodeFromString(Project.serializer(), text).countIn)
    }

    @Test
    fun aDocumentWithAKnownCountInReadsIt() {
        val text = featureNineDocument.dropLast(1) + ",\"countIn\":\"OFF\"}"
        assertEquals(CountIn.OFF, json.decodeFromString(Project.serializer(), text).countIn)
    }

    @Test
    fun aNewProjectCountsInOneBar() {
        assertEquals(CountIn.ONE_BAR, Project.new(ProjectId("n"), "Song").countIn)
    }
}
