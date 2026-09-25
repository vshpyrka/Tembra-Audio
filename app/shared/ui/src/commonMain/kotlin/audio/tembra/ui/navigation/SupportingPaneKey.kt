package audio.tembra.ui.navigation

import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.metadata

/** Navigation 3 metadata key under which an entry declares its [SupportingPane] (research R2). */
object SupportingPaneKey : NavMetadataKey<SupportingPane> {
    /** Metadata is stored under the key's string form; keep it stable rather than the object's identity hash. */
    override fun toString(): String = "SupportingPaneKey"
}

/** Entry metadata declaring [pane] as the entry's supporting content. */
fun supportingPane(pane: SupportingPane): Map<String, Any> = metadata { put(SupportingPaneKey, pane) }
