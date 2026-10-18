package audio.tembra.data.projects

import audio.tembra.core.project.GridSize
import audio.tembra.core.project.ZoomLevel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The per-project view conveniences remembered on the device in `view.json`, never in the document (spec FR-015,
 * FR-018, FR-049): the zoom level (null means "fit") and the grid. Written directly on change, not autosaved.
 */
@Serializable
data class ProjectViewState(
    @SerialName("zoom") val zoom: ZoomLevel? = null,
    @SerialName("grid") val grid: GridSize = GridSize.Default,
)
