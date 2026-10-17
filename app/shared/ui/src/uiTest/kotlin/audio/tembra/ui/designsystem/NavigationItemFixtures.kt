package audio.tembra.ui.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import audio.tembra.ui.theme.TembraIcon
import kotlin.test.assertTrue

/** The five navigation items as the design-system components see them: no dependency on the destination registry. */
internal val fiveNavigationItems = listOf(
    NavigationItemSpec("timeline", "Timeline", TembraIcon.Timeline),
    NavigationItemSpec("tuner", "Tuner", TembraIcon.Tuner),
    NavigationItemSpec("metronome", "Metronome", TembraIcon.Metronome),
    NavigationItemSpec("projects", "Projects", TembraIcon.Projects),
    NavigationItemSpec("profile", "Profile", TembraIcon.Profile),
)

/** The platform density with the system font scale replaced, as the largest accessibility text size would set it. */
@Composable
internal fun largeFonts(fontScale: Float): Density = Density(density = LocalDensity.current.density, fontScale = fontScale)

/** Whether this lane's platform can enter keyboard input mode: declared per platform, never probed silently. */
internal expect val supportsKeyboardInputMode: Boolean

/**
 * Switches Compose to keyboard input mode for a key-driven test (clickables stay unfocusable in touch mode). A lane
 * declared without keyboard mode skips by that declaration and returns false; a lane that declares it must enter it,
 * or the test fails.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.enterKeyboardMode(manager: InputModeManager?): Boolean {
    if (!supportsKeyboardInputMode) return false
    val keyboard = runOnUiThread { checkNotNull(manager) { "no InputModeManager was captured from the content" }.requestInputMode(InputMode.Keyboard) }
    assertTrue(keyboard, "this lane declares keyboard input mode but the platform stayed in touch mode")
    return true
}

/** Asserts the node is displayed and its unclipped bounds sit inside [container] (half a dp of rounding tolerance). */
@OptIn(ExperimentalTestApi::class)
internal fun SemanticsNodeInteraction.assertDisplayedWithin(container: DpRect, what: String): SemanticsNodeInteraction {
    assertIsDisplayed()
    val bounds = getUnclippedBoundsInRoot()
    val tolerance = 0.5.dp
    assertTrue(bounds.left >= container.left - tolerance, "$what left edge ${bounds.left} is outside ${container.left}")
    assertTrue(bounds.right <= container.right + tolerance, "$what right edge ${bounds.right} is outside ${container.right}")
    assertTrue(bounds.top >= container.top - tolerance, "$what top edge ${bounds.top} is outside ${container.top}")
    assertTrue(bounds.bottom <= container.bottom + tolerance, "$what bottom edge ${bounds.bottom} is outside ${container.bottom}")
    return this
}

/** The text layout of a text node, through its semantics action. */
internal fun SemanticsNodeInteraction.textLayoutResult(): TextLayoutResult {
    val results = mutableListOf<TextLayoutResult>()
    val action = fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action
    assertTrue(action != null && action(results), "no text layout result on the node")
    return results.single()
}

/** FR-014 and FR-015 for one item: tab role, its label as description, and a 44 dp target both ways. */
internal fun SemanticsNodeInteraction.assertIsAccessibleNavigationItem(label: String): SemanticsNodeInteraction {
    assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
    assertContentDescriptionEquals(label)
    assertWidthIsAtLeast(44.dp)
    assertHeightIsAtLeast(44.dp)
    return this
}

/** True when any pixel of [image] is within [tolerance] of [color] per channel. */
internal fun ImageBitmap.containsColor(color: Color, tolerance: Float = 0.02f): Boolean {
    val pixels = toPixelMap()
    for (y in 0 until pixels.height) {
        for (x in 0 until pixels.width) {
            val p = pixels[x, y]
            if (kotlin.math.abs(p.red - color.red) <= tolerance &&
                kotlin.math.abs(p.green - color.green) <= tolerance &&
                kotlin.math.abs(p.blue - color.blue) <= tolerance
            ) return true
        }
    }
    return false
}

/** True when [other] has the same size and every pixel matches this image's within [tolerance] per channel. */
internal fun ImageBitmap.looksLike(other: ImageBitmap, tolerance: Float = 0.01f): Boolean {
    if (width != other.width || height != other.height) return false
    val a = toPixelMap()
    val b = other.toPixelMap()
    for (y in 0 until height) {
        for (x in 0 until width) {
            val p = a[x, y]
            val q = b[x, y]
            if (kotlin.math.abs(p.red - q.red) > tolerance ||
                kotlin.math.abs(p.green - q.green) > tolerance ||
                kotlin.math.abs(p.blue - q.blue) > tolerance
            ) return false
        }
    }
    return true
}

/** The pixel at ([x], [y]) of [image] as a colour, for corner and edge probes. */
internal fun ImageBitmap.colorAt(x: Int, y: Int): Color = toPixelMap()[x, y]
