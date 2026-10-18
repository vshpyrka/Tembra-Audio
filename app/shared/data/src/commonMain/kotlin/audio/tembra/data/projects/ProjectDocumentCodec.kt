package audio.tembra.data.projects

import audio.tembra.core.project.ProjectDocument
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Reads and writes `project.json` (contracts/project-document.md): pretty-printed with two-space indentation in
 * declaration order, unknown keys ignored, nulls omitted, defaults written, `-Infinity` allowed. [decode] reads the
 * format version first, refuses a newer document, migrates an older one, reports tempo and signature out of range by
 * name before the typed decode, then validates. The same [json] serialises the library index and the view state.
 */
class ProjectDocumentCodec(private val migrations: ProjectDocumentMigrations = ProjectDocumentMigrations()) {

    @OptIn(ExperimentalSerializationApi::class)
    internal val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        prettyPrint = true
        prettyPrintIndent = "  "
        allowSpecialFloatingPointValues = true
    }

    /** The document text, ending with a newline so the file diffs cleanly. */
    fun encode(document: ProjectDocument): String = json.encodeToString(ProjectDocument.serializer(), document) + "\n"

    fun decode(text: String): DecodeResult {
        val root = try {
            json.parseToJsonElement(text)
        } catch (failure: SerializationException) {
            return DecodeResult.Malformed(failure.message ?: "The document is not valid JSON")
        }
        val envelope = root as? JsonObject ?: return DecodeResult.Malformed("The document is not a JSON object")
        val version = (envelope["formatVersion"] as? JsonPrimitive)?.intOrNull ?: return DecodeResult.Malformed("formatVersion is missing")
        if (version > ProjectDocument.CURRENT_VERSION) return DocumentTooNew(version)
        val migrated = if (version < ProjectDocument.CURRENT_VERSION) {
            migrations.migrate(envelope, fromVersion = version) ?: return DecodeResult.Malformed("No migration from format version $version")
        } else {
            envelope
        }
        val project = migrated["project"] as? JsonObject ?: return DecodeResult.Malformed("project is missing")
        val headerFailures = listOfNotNull(
            (project["tempo"] as? JsonPrimitive)?.intOrNull?.let(ProjectValidator::validateTempo),
            (project["timeSignature"] as? JsonPrimitive)?.contentOrNull?.let(ProjectValidator::validateTimeSignature),
        )
        if (headerFailures.isNotEmpty()) return DecodeResult.Invalid(headerFailures)
        val document = try {
            json.decodeFromJsonElement(ProjectDocument.serializer(), migrated)
        } catch (failure: SerializationException) {
            return DecodeResult.Malformed(failure.message ?: "The document does not match the format")
        } catch (failure: IllegalArgumentException) {
            return DecodeResult.Malformed(failure.message ?: "The document holds a value out of range")
        }
        val failures = ProjectValidator.validate(document.project)
        if (failures.isNotEmpty()) return DecodeResult.Invalid(failures)
        return DecodeResult.Decoded(document.copy(formatVersion = ProjectDocument.CURRENT_VERSION))
    }
}
