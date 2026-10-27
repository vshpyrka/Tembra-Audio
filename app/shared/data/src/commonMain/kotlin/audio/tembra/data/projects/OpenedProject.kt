package audio.tembra.data.projects

import audio.tembra.core.project.AudioAssetId
import audio.tembra.core.project.Project

/**
 * What [ProjectRepository.open] hands the session: the validated project, the device paths of its audio copies and
 * overviews, and the assets whose file is missing or whose format this build cannot play (drawn greyed, never
 * opened in the engine).
 */
data class OpenedProject(
    val project: Project,
    private val audioPaths: Map<AudioAssetId, String>,
    private val overviewPaths: Map<AudioAssetId, String>,
    val missingAssets: Set<AudioAssetId>,
) {
    /** The audio copy's path; null for a missing or unavailable asset. */
    fun audioPath(assetId: AudioAssetId): String? = audioPaths[assetId]

    /** The `.peaks` path the overview is (or will be) built at; null for an asset the project does not list. */
    fun overviewPath(assetId: AudioAssetId): String? = overviewPaths[assetId]
}
