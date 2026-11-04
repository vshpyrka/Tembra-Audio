package audio.tembra.ui.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.core.project.AudioAssetId
import audio.tembra.core.project.Beats
import audio.tembra.core.project.ClipId
import audio.tembra.core.project.GainDb
import audio.tembra.core.project.ProjectId
import audio.tembra.core.project.TrackId
import audio.tembra.core.timeline.TimelineViewport
import audio.tembra.data.projects.CardOverview
import audio.tembra.data.projects.OverviewStatus
import audio.tembra.ui.projects.ProjectCardUi
import audio.tembra.ui.projects.ProjectsCallbacks
import audio.tembra.ui.projects.ProjectsLayout
import audio.tembra.ui.projects.ProjectsTestTags
import audio.tembra.ui.projects.components.ProjectCard
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.ClipUi
import audio.tembra.ui.timeline.Selection
import audio.tembra.ui.timeline.TimelineTestTags
import audio.tembra.ui.timeline.TrackUi
import audio.tembra.ui.timeline.components.TrackHeader
import audio.tembra.ui.timeline.components.TrackHeaderCompact
import audio.tembra.ui.timeline.components.TrackLane
import kotlinx.collections.immutable.persistentListOf
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Menus at the pointer (feature 011, spec FR-012; contracts/pointer-input.md): a secondary click on a clip, a track
 * header (both forms) or a project card opens that item's menu where the pointer is, and never runs the item's primary
 * action (on Android a right-click otherwise also fires `clickable`'s `onClick`).
 */
@OptIn(ExperimentalTestApi::class)
class SecondaryClickTest {

    private val calls = mutableListOf<String>()
    private val clip = ClipUi(ClipId("c1"), AudioAssetId("a".repeat(64)), "drums", Beats(0.0), Beats(16.0), 0, 48000, 48000, null, OverviewStatus.Ready, true)
    private val track = TrackUi(TrackId("t1"), "Drums", null, 0, GainDb.Unity, false, false, true, persistentListOf(clip))

    /**
     * Asserts the menu tagged [menu] opened with a corner within 24 dp of the click at [local] in the node tagged [item]
     * (screen coordinates: the menu is a pop-up, a window of its own).
     */
    private fun ComposeUiTest.assertMenuAt(menu: String, item: String, local: Offset, density: Float) {
        waitForIdle()
        val click = onNodeWithTag(item).fetchSemanticsNode().positionOnScreen + local
        val menuNode = onNodeWithTag(menu).fetchSemanticsNode()
        val topLeft = menuNode.positionOnScreen
        val bottomRight = topLeft + Offset(menuNode.size.width.toFloat(), menuNode.size.height.toFloat())
        val slack = 24f * density
        val nearX = abs(topLeft.x - click.x) <= slack || abs(bottomRight.x - click.x) <= slack
        val nearY = abs(topLeft.y - click.y) <= slack || abs(bottomRight.y - click.y) <= slack
        assertTrue(nearX && nearY, "the menu ($topLeft – $bottomRight px) is not at the pointer $click px")
    }

    @Test
    fun aClipOpensItsMenuAtThePointer() = runComposeUiTest {
        var menu by mutableStateOf<ClipId?>(null)
        var density = 1f
        setContent {
            TembraTheme {
                density = androidx.compose.ui.platform.LocalDensity.current.density
                Box(Modifier.requiredSize(800.dp, 400.dp).padding(top = 160.dp)) {
                    TrackLane(
                        track = track, index = 0, firstClipIndex = 0,
                        viewport = TimelineViewport(pixelsPerBeat = 40.0, scrollBeats = Beats.Zero, widthPx = 800.0 * density, densityScale = density.toDouble()),
                        selection = Selection.None, signature = TimeSignature.FourFour, height = 96.dp,
                        onSelectClip = { calls += "select" }, onClearSelection = { calls += "clear" },
                        menuClip = menu, onMenuClip = { menu = it },
                    )
                }
            }
        }
        val local = Offset(300f * density, 60f * density)
        onNodeWithTag(TimelineTestTags.clip(0)).performMouseInput { rightClick(local) }
        assertEquals(ClipId("c1"), menu)
        assertMenuAt("${TimelineTestTags.clip(0)}.menu", TimelineTestTags.clip(0), local, density)
    }

    @Test
    fun bothTrackHeaderFormsOpenTheTrackMenuAtThePointerAndDoNotSelect() = runComposeUiTest {
        var menuOpen by mutableStateOf(false)
        var expanded by mutableStateOf(true)
        var density = 1f
        setContent {
            TembraTheme {
                density = androidx.compose.ui.platform.LocalDensity.current.density
                // Pinned top-left so an 800 dp host that overflows a phone's window still starts on screen (T135).
                Box(Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).requiredSize(800.dp, 400.dp).padding(top = 160.dp)) {
                    if (expanded) {
                        TrackHeader(
                            track = track, index = 0, selected = false, expanded = false, isFirst = true, isLast = true,
                            onSelect = { calls += "select" }, onMute = {}, onSolo = {}, onGain = {}, onExpand = {}, onSettings = {},
                            onAddAudio = {}, onMoveUp = {}, onMoveDown = {}, onDelete = {},
                            menuOpen = menuOpen, onMenuOpen = { menuOpen = it },
                        )
                    } else {
                        TrackHeaderCompact(
                            track = track, index = 0, selected = false, isFirst = true, isLast = true,
                            onSelect = { calls += "select" }, onMute = {}, onSolo = {}, onSettings = {},
                            onAddAudio = {}, onMoveUp = {}, onMoveDown = {}, onDelete = {},
                            menuOpen = menuOpen, onMenuOpen = { menuOpen = it },
                        )
                    }
                }
            }
        }
        for (form in listOf(true, false)) {
            expanded = form
            menuOpen = false
            waitForIdle()
            val local = Offset(30f * density, 20f * density)
            onNodeWithTag(TimelineTestTags.header(0)).performMouseInput { rightClick(local) }
            assertTrue(menuOpen, "the secondary click opened no menu (expanded=$form)")
            assertMenuAt(TimelineTestTags.TRACK_MENU, TimelineTestTags.header(0), local, density)
        }
        assertTrue("select" !in calls, "a secondary click selected the track: $calls")
    }

    @Test
    fun aProjectCardOpensItsMenuAtThePointerAndDoesNotOpenTheProject() = runComposeUiTest {
        val card = ProjectCardUi(
            id = ProjectId("p1"), name = "Sketch", description = "", tempoText = "124 BPM", keyText = "—", lengthText = "00:30.96",
            tracksText = "4 tracks", sizeText = "17 MB", relativeTime = "2 m ago", isActive = false, isDemo = false,
            overview = CardOverview(4, byteArrayOf(10, 100, -1, 50), byteArrayOf(0, 1, 2, 3)),
        )
        var density = 1f
        setContent {
            TembraTheme {
                density = androidx.compose.ui.platform.LocalDensity.current.density
                Box(Modifier.requiredSize(400.dp, 500.dp).padding(top = 160.dp)) {
                    ProjectCard(card = card, layout = ProjectsLayout.OneColumn, callbacks = ProjectsCallbacks.None.copy(onOpen = { calls += "open" }))
                }
            }
        }
        val local = Offset(60f * density, 90f * density)
        onNodeWithTag(ProjectsTestTags.card(card.id)).performMouseInput { rightClick(local) }
        assertMenuAt(ProjectsTestTags.cardMenu(card.id), ProjectsTestTags.card(card.id), local, density)
        assertTrue("open" !in calls, "a secondary click opened the project: $calls")
    }
}
