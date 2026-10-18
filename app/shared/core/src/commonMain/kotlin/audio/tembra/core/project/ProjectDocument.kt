package audio.tembra.core.project

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The on-disk envelope of `project.json` (contracts/project-document.md): the format version first, then the
 * project. Readers read [formatVersion] before anything else and migrate upward or refuse a newer document.
 */
@Serializable
data class ProjectDocument(
    @SerialName("formatVersion") val formatVersion: Int = CURRENT_VERSION,
    @SerialName("project") val project: Project,
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}
