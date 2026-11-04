package audio.tembra.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import audio.tembra.core.project.LibraryFilter
import audio.tembra.core.project.LibrarySort
import audio.tembra.core.project.MusicalKey
import audio.tembra.core.project.Project
import audio.tembra.core.project.ProjectId
import audio.tembra.core.project.ProjectNames
import audio.tembra.core.project.RenameProject
import audio.tembra.core.project.SetDescription
import audio.tembra.core.project.SetKey
import audio.tembra.core.timeline.TimeFormat
import audio.tembra.data.projects.LibraryIndex
import audio.tembra.data.projects.OpenResult
import audio.tembra.data.projects.ProjectRepository
import audio.tembra.engine.perf.PerfSection
import audio.tembra.engine.perf.PerfTrace
import audio.tembra.ui.drop.DropOutcome
import audio.tembra.ui.drop.DropPlan
import audio.tembra.ui.drop.ProjectFromAudio
import audio.tembra.ui.drop.SessionProjectFromAudio
import audio.tembra.ui.projects.components.RelativeTime
import audio.tembra.ui.session.ProjectSession
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlin.time.Clock
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The library's state holder (contracts/projects-library-screen.md; spec FR-050 to FR-054): the repository's index,
 * the session's open project and the app-scoped [LibraryViewStateHolder] (search, filter and sort survive this
 * entry-scoped ViewModel, FR-053) combine into one [ProjectsUiState]. Opening goes through the session and ends in
 * [events]; edits to the open project are session edits (undoable), to others repository saves. Named
 * `viewModelScope` launches: `openProject`, `newProject`, `duplicate`, `restoreDemo`, `applyEdit`, `deleteProject`.
 */
@Inject
@ContributesIntoMap(AppScope::class)
@ViewModelKey
class ProjectsViewModel(
    private val repository: ProjectRepository,
    private val session: ProjectSession,
    private val holder: LibraryViewStateHolder,
    /** Optional binding: tests pass a fixed clock for the relative times and the Recent filter. */
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private val dialog = MutableStateFlow<ProjectsDialog?>(null)
    private val projectFromAudio: ProjectFromAudio = SessionProjectFromAudio(repository, session)
    private var firstMapping = true

    val uiState: StateFlow<ProjectsUiState> = combine(
        repository.library(),
        session.state.map { it.project?.id }.distinctUntilChanged(),
        holder.query,
        dialog,
        ::mapState,
    ).stateIn(viewModelScope, SharingStarted.Eagerly, ProjectsUiState())

    private val _events = Channel<ProjectsEvent>(Channel.BUFFERED)
    val events: Flow<ProjectsEvent> = _events.receiveAsFlow()

    private val _messages = Channel<ProjectsMessage>(Channel.BUFFERED)
    val messages: Flow<ProjectsMessage> = _messages.receiveAsFlow()

    // --- Query -------------------------------------------------------------------------------------------------------

    fun setQuery(text: String) = holder.query.update { it.copy(text = text) }

    fun setFilter(filter: LibraryFilter) = holder.query.update { it.copy(filter = filter) }

    fun setSort(sort: LibrarySort) = holder.query.update { it.copy(sort = sort) }

    // --- Opening -----------------------------------------------------------------------------------------------------

    fun open(id: ProjectId) {
        viewModelScope.launch(CoroutineName("openProject")) { openInTimeline(id) }
    }

    /** "Project N" at the defaults of spec FR-003, then opened (FR-052). */
    fun newProject() {
        viewModelScope.launch(CoroutineName("newProject")) {
            val project = repository.create(repository.library().first().nextProjectName())
            openInTimeline(project.id)
        }
    }

    /**
     * Files dropped on the library (feature 011, spec FR-026): "Project N" with one track per audio file, opened on the
     * Timeline; the items the drop left out share the import's message. With no audio file, a message says so.
     */
    fun drop(outcome: DropOutcome) {
        val files = when (val plan = outcome.plan) {
            is DropPlan.NewProject -> plan.files
            // Projects' zone plans a new project; any other plan's files make one too.
            is DropPlan.NewTracks -> plan.files
            is DropPlan.AddToTrack -> listOf(plan.file)
            DropPlan.Nothing -> {
                _messages.trySend(ProjectsMessage.NoAudioFound(outcome.skipped))
                return
            }
        }
        viewModelScope.launch(CoroutineName("dropProject")) {
            if (projectFromAudio.start(files, outcome.skipped)) _events.trySend(ProjectsEvent.OpenedInTimeline) else _messages.trySend(ProjectsMessage.CouldNotOpen)
        }
    }

    // --- Card menu ---------------------------------------------------------------------------------------------------

    fun rename(id: ProjectId) = showDialog(id) { ProjectsDialog.Rename(id, it.name) }

    fun describe(id: ProjectId) = showDialog(id) { ProjectsDialog.Describe(id, it.description) }

    fun chooseKey(id: ProjectId) = showDialog(id) { ProjectsDialog.Key(id, it.key) }

    fun delete(id: ProjectId) = showDialog(id) { ProjectsDialog.Delete(id, it.name) }

    fun duplicate(id: ProjectId) {
        viewModelScope.launch(CoroutineName("duplicate")) {
            if (repository.duplicate(id) == null) _messages.trySend(ProjectsMessage.CouldNotDuplicate)
        }
    }

    /** A fresh demo replaces the current one; if the demo was open, the new one opens in its place (spec FR-045). */
    fun restoreDemo() {
        viewModelScope.launch(CoroutineName("restoreDemo")) {
            val openId = session.state.value.project?.id
            val wasOpen = openId != null && repository.library().first().entries.any { it.id == openId && it.origin == audio.tembra.data.projects.ProjectOrigin.Demo }
            // A take recording into the open demo refuses the restore unless the user ends it (feature 010, FR-018).
            if (wasOpen && !leaveAllowed()) return@launch
            val demo = repository.restoreDemo()
            if (wasOpen) openInTimeline(demo.id)
        }
    }

    fun dismissDialog() {
        dialog.value = null
    }

    fun confirmRename(name: String) {
        val current = dialog.value as? ProjectsDialog.Rename ?: return
        val trimmed = name.trim()
        if (!ProjectNames.isValidProjectName(trimmed)) {
            dialog.value = current.copy(name = name, error = if (trimmed.isEmpty()) ProjectsCopy.nameEmpty else ProjectsCopy.nameTooLong)
            return
        }
        dialog.value = null
        applyEdit(current.projectId, RenameProject(trimmed)) { it.copy(name = trimmed) }
    }

    fun confirmDescription(description: String) {
        val current = dialog.value as? ProjectsDialog.Describe ?: return
        val trimmed = description.trim()
        if (!ProjectNames.isValidDescription(trimmed)) {
            dialog.value = current.copy(description = description, error = ProjectsCopy.descriptionTooLong)
            return
        }
        dialog.value = null
        applyEdit(current.projectId, SetDescription(trimmed)) { it.copy(description = trimmed) }
    }

    fun confirmKey(key: MusicalKey?) {
        val current = dialog.value as? ProjectsDialog.Key ?: return
        dialog.value = null
        applyEdit(current.projectId, SetKey(key)) { it.copy(key = key) }
    }

    /** Deleting the open project opens the most recently edited remaining one, or a new empty one (spec FR-052). */
    fun confirmDelete() {
        val current = dialog.value as? ProjectsDialog.Delete ?: return
        dialog.value = null
        viewModelScope.launch(CoroutineName("deleteProject")) {
            val wasOpen = session.state.value.project?.id == current.projectId
            repository.delete(current.projectId)
            if (!wasOpen) return@launch
            val index = repository.library().first()
            val next = index.entries.maxByOrNull { it.lastEdited }?.id ?: repository.create(index.nextProjectName()).id
            session.open(next)
        }
    }

    // --- Helpers -----------------------------------------------------------------------------------------------------

    private suspend fun openInTimeline(id: ProjectId) {
        if (!leaveAllowed()) return
        session.open(id)
        if (session.state.value.project?.id == id) _events.trySend(ProjectsEvent.OpenedInTimeline) else _messages.trySend(ProjectsMessage.CouldNotOpen)
    }

    /** A running take refuses the switch unless the user ends it through the stop-and-leave dialog (feature 010, FR-018, FR-022). */
    private suspend fun leaveAllowed(): Boolean = !session.state.value.recording.takeRunning || session.confirmLeave()

    private fun showDialog(id: ProjectId, build: (ProjectCardUi) -> ProjectsDialog) {
        val card = uiState.value.cards.firstOrNull { it.id == id } ?: return
        dialog.value = build(card)
    }

    /** The open project edits through the session (undoable); any other through the repository's document. */
    private fun applyEdit(id: ProjectId, edit: audio.tembra.core.project.ProjectEdit, transform: (Project) -> Project) {
        if (session.state.value.project?.id == id) {
            session.edit(edit)
            return
        }
        viewModelScope.launch(CoroutineName("applyEdit")) {
            when (val opened = repository.open(id)) {
                is OpenResult.Opened -> repository.save(transform(opened.opened.project))
                is OpenResult.Unavailable, is OpenResult.Missing -> _messages.trySend(ProjectsMessage.CouldNotOpen)
            }
        }
    }

    private fun mapState(index: LibraryIndex, openId: ProjectId?, query: audio.tembra.core.project.LibraryQuery, dialog: ProjectsDialog?): ProjectsUiState {
        val first = firstMapping
        firstMapping = false
        return if (first) PerfTrace.section(PerfSection.TIMELINE_LIBRARY_OPEN) { buildState(index, openId, query, dialog) } else buildState(index, openId, query, dialog)
    }

    private fun buildState(index: LibraryIndex, openId: ProjectId?, query: audio.tembra.core.project.LibraryQuery, dialog: ProjectsDialog?): ProjectsUiState {
        val now = clock.now().toEpochMilliseconds()
        val summaries = index.entries.map { it.toSummary() }
        val shown = query.apply(summaries, now)
        val cards = shown.map { summary ->
            ProjectCardUi(
                id = summary.id,
                name = summary.name,
                description = summary.description,
                tempoText = ProjectsCopy.tempo(summary.tempo.bpm),
                keyText = summary.key?.displayName ?: ProjectsCopy.noKey,
                lengthText = TimeFormat.clock(summary.lengthSeconds),
                tracksText = ProjectsCopy.tracks(summary.trackCount),
                sizeText = sizeText(summary.byteSize),
                relativeTime = RelativeTime.format(summary.lastEditedEpochMs, now),
                isActive = summary.id == openId,
                isDemo = summary.isDemo,
                overview = index.entry(summary.id)?.overview ?: audio.tembra.data.projects.CardOverview.Empty,
                key = summary.key,
            )
        }
        return ProjectsUiState(
            loaded = true,
            cards = cards.toPersistentList(),
            total = summaries.size,
            query = query,
            countText = query.countText(shown.size, summaries.size),
            dialog = dialog,
        )
    }

    private companion object {
        const val KILOBYTE = 1024.0
        const val MEGABYTE = 1024.0 * 1024.0

        /** "142 MB", "1.2 MB", "820 KB", "0 B". */
        fun sizeText(bytes: Long): String = when {
            bytes >= 10 * MEGABYTE -> "${(bytes / MEGABYTE).toInt()} MB"
            bytes >= MEGABYTE -> "${((bytes / MEGABYTE) * 10).toInt() / 10.0} MB"
            bytes >= KILOBYTE -> "${(bytes / KILOBYTE).toInt()} KB"
            else -> "$bytes B"
        }
    }
}
