package audio.tembra.ui.perf

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.semantics.semantics

internal actual fun Modifier.anchorRoot(): Modifier = semantics { testTagsAsResourceId = true }
