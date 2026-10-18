package audio.tembra.data.projects

import audio.tembra.core.project.ProjectId
import audio.tembra.data.AppDirectories
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

/**
 * The folder layout of contracts/project-storage.md: `<documents>/projects/` holds `library.json` and one folder per
 * project; `<cache>/projects/<id>/` holds the rebuildable overviews.
 */
class ProjectFolders(directories: AppDirectories) {
    val root: Path = directories.documents.toPath() / "projects"
    val cacheRoot: Path = directories.cache.toPath() / "projects"
    val libraryIndex: Path = root / "library.json"
    val libraryIndexStaging: Path = root / "library.json.tmp"

    fun folder(id: ProjectId): ProjectFolder = ProjectFolder(id, root / id.value, cacheRoot / id.value)

    /** The ids of every folder that holds a `project.json`, in name order. */
    fun projectIds(fileSystem: FileSystem): List<ProjectId> {
        if (!fileSystem.exists(root)) return emptyList()
        return fileSystem.list(root)
            .filter { fileSystem.metadataOrNull(it)?.isDirectory == true && fileSystem.exists(it / ProjectFolder.DOCUMENT_NAME) }
            .map { ProjectId(it.name) }
            .sortedBy { it.value }
    }
}
