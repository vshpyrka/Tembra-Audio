package audio.tembra.ui.drop

import audio.tembra.data.projects.PickedFile
import audio.tembra.ui.navigation.Destination
import audio.tembra.ui.navigation.ShellViewModel
import audio.tembra.ui.session.FakePlaybackGuard
import audio.tembra.ui.session.FakeSessionActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Audio opened with the app from the system's file manager (feature 011, spec FR-026a; contracts/drop-import.md
 * behaviour 5): the files become a new project with one track per audio file, shown on the Timeline; while a project
 * plays, the stop-and-leave question comes first and "Keep playing" drops the request; non-audio items are named in the
 * skipped message.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OpenedFilesFlowTest {

    private val dispatcher = StandardTestDispatcher()
    private val opened = FakeOpenedFiles()
    private val projects = FakeProjectFromAudio()
    private lateinit var guard: FakePlaybackGuard
    private lateinit var shell: ShellViewModel

    private fun audio(name: String) = DroppedItem.AudioFile(PickedFile(name) { error("not read") })

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** Stopped, the shell shows Projects; playing, it stays on the Timeline (leaving it would ask first). */
    private fun start(playing: Boolean) {
        guard = FakePlaybackGuard(isPlaying = playing)
        shell = ShellViewModel(guard, FakeSessionActivity(), opened, projects)
        if (!playing) shell.select(Destination.Projects)
    }

    @Test
    fun openedAudioBecomesANewProjectOnTheTimeline() = runTest(dispatcher) {
        start(playing = false)
        runCurrent()
        opened.open(audio("kick.wav"), audio("bass.flac"), DroppedItem.Other("cover.png"))
        runCurrent()
        assertEquals(listOf(listOf("kick.wav", "bass.flac") to listOf(SkippedItem("cover.png", SkipReason.NotAudio))), projects.started)
        assertEquals(Destination.Timeline, shell.uiState.value.selected)
        assertEquals(0, guard.asked)
    }

    @Test
    fun whilePlayingTheQuestionComesFirstAndKeepPlayingDropsTheRequest() = runTest(dispatcher) {
        start(playing = true)
        runCurrent()
        opened.open(audio("kick.wav"))
        runCurrent()
        assertEquals(1, guard.asked)
        assertEquals(emptyList(), projects.started)
        guard.answerLeave(false)
        runCurrent()
        assertEquals(emptyList(), projects.started)
        assertEquals(true, guard.isPlaying)
    }

    @Test
    fun whilePlayingStopAndLeaveOpensTheFiles() = runTest(dispatcher) {
        start(playing = true)
        runCurrent()
        opened.open(audio("kick.wav"))
        runCurrent()
        guard.answerLeave(true)
        runCurrent()
        assertEquals(listOf("kick.wav"), projects.started.single().first)
        assertEquals(Destination.Timeline, shell.uiState.value.selected)
    }

    @Test
    fun withNoAudioNothingHappens() = runTest(dispatcher) {
        start(playing = true)
        runCurrent()
        opened.open(DroppedItem.Other("notes.txt"))
        runCurrent()
        assertEquals(0, guard.asked)
        assertEquals(emptyList(), projects.started)
    }
}
