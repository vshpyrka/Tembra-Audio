package audio.tembra.data.projects

import audio.tembra.core.project.ProjectDocument
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Runs the registered [DocumentMigration]s upward, one version at a time, stamping `formatVersion` after each
 * (spec FR-046; contracts/project-document.md rule 2). Version 1 has no steps; a missing step answers null and the
 * codec refuses the document.
 */
class ProjectDocumentMigrations(private val steps: List<DocumentMigration> = emptyList()) {

    fun migrate(document: JsonObject, fromVersion: Int, toVersion: Int = ProjectDocument.CURRENT_VERSION): JsonObject? {
        var current = document
        var version = fromVersion
        while (version < toVersion) {
            val step = steps.firstOrNull { it.fromVersion == version } ?: return null
            version += 1
            current = JsonObject(step.transform(current) + ("formatVersion" to JsonPrimitive(version)))
        }
        return current
    }
}
