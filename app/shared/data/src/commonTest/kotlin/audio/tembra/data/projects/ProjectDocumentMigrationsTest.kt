package audio.tembra.data.projects

import audio.tembra.core.metronome.Tempo
import audio.tembra.core.project.ProjectDocument
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Explicit migrations between document versions (spec FR-046; contracts/project-document.md rule 2). */
class ProjectDocumentMigrationsTest {

    /** A pre-release draft wrote the tempo as "bpm"; the registered step renames it. */
    private val zeroToOne = DocumentMigration(fromVersion = 0) { document ->
        val project = document.getValue("project").jsonObject
        val renamed = JsonObject(project.filterKeys { it != "bpm" } + ("tempo" to project.getValue("bpm")))
        JsonObject(document + ("project" to renamed))
    }

    private fun versionZeroText(): String = ProjectDocumentCodec().encode(ProjectDocument(project = DemoProject.build(DemoProjectIds.fixed())))
        .replace("\"formatVersion\": 1", "\"formatVersion\": 0")
        .replace("\"tempo\": 124", "\"bpm\": 124")

    @Test
    fun aRegisteredMigrationRunsForAnOlderDocument() {
        val codec = ProjectDocumentCodec(ProjectDocumentMigrations(listOf(zeroToOne)))
        val result = assertIs<DecodeResult.Decoded>(codec.decode(versionZeroText()))
        assertEquals(ProjectDocument.CURRENT_VERSION, result.document.formatVersion)
        assertEquals(Tempo(124), result.document.project.tempo)
    }

    @Test
    fun theCurrentVersionIsANoOp() {
        val migrations = ProjectDocumentMigrations(listOf(zeroToOne))
        val document = JsonObject(mapOf("formatVersion" to JsonPrimitive(1)))
        assertEquals(document, migrations.migrate(document, fromVersion = ProjectDocument.CURRENT_VERSION))
    }

    @Test
    fun anOlderDocumentWithoutAMigrationIsMalformed() {
        val result = ProjectDocumentCodec(ProjectDocumentMigrations()).decode(versionZeroText())
        assertIs<DecodeResult.Malformed>(result)
    }

    @Test
    fun migrationsRunInVersionOrderAndStampTheVersion() {
        val steps = listOf(
            DocumentMigration(1) { JsonObject(it + ("one" to JsonPrimitive(true))) },
            DocumentMigration(0) { JsonObject(it + ("zero" to JsonPrimitive(true))) },
        )
        val migrated = ProjectDocumentMigrations(steps).migrate(JsonObject(mapOf("formatVersion" to JsonPrimitive(0))), fromVersion = 0)
        assertEquals(JsonPrimitive(true), migrated?.get("zero"))
        assertEquals(JsonPrimitive(true), migrated?.get("one"))
        assertEquals(JsonPrimitive(2), migrated?.get("formatVersion"))
    }
}
