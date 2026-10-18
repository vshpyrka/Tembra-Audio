package audio.tembra.data.projects

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Whether a library project is the bundled demo (spec FR-045) or the user's own. */
@Serializable
enum class ProjectOrigin {
    @SerialName("demo")
    Demo,

    @SerialName("user")
    User,
}
