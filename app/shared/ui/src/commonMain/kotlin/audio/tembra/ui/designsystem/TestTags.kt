package audio.tembra.ui.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/** Applies [tag] as a test tag when one was given; the design-system components take their tags as optional parameters. */
internal fun Modifier.testTagIfSet(tag: String?): Modifier = if (tag == null) this else testTag(tag)
