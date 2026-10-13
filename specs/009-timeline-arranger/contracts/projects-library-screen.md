# Contract: the Projects library screen

**Where**: `:shared:ui`, package `audio.tembra.ui.projects` (+ `components`). Spec User Story 7,
FR-050 to FR-054; research R9, R13, R15; data model §4, §5.

## Files

```
ui/projects/
  ProjectsScreen.kt            wiring: metroViewModel(), ReportFirstContentDrawn(entries loaded), messages → snackbar
  ProjectsScreenContent.kt     stateless: (state, callbacks, layout, modifier): header row (count, New project), LibrarySearchField, LibraryFilterChips, SortMenu, the card grid, dialogs
  ProjectsViewModel.kt         @Inject @ContributesIntoMap @ViewModelKey; collects repository.library() + session.state.project?.id + LibraryViewStateHolder.state; LibraryQuery in memory; intents
  LibraryViewStateHolder.kt    @Inject @SingleIn(AppScope): MutableStateFlow<LibraryViewState(query, filter, sort)>; the ViewModel is entry-scoped and dies on every destination switch, so this holder is what keeps the search, filter and sort "while the app runs" (FR-053); reset on launch by construction
  ProjectsUiState.kt, ProjectsCallbacks.kt, ProjectsLayout.kt (OneColumn < 840 dp, TwoColumns ≥ 840, ThreeColumns ≥ 1200), ProjectsCopy.kt, ProjectsDialog.kt
  components/
    ProjectCard.kt             badges (Active, Demo), relative time, name, description, ProjectOverviewChart, stats row (tempo, key or "—", length, tracks), footer (size), Resume/Open button, menu button
    ProjectOverviewChart.kt    Canvas: 48 bars (compact) or a line (expanded) from CardOverview, tinted per span
    ProjectBadge.kt, ProjectStat.kt, ProjectCardMenu.kt
    LibrarySearchField.kt      TembraTextField with search icon and clear
    LibraryFilterChips.kt      TembraChipRow: All, Recent
    SortMenu.kt                MenuPill: Last edited, Name
    RelativeTime.kt            pure formatter ("2 m ago", "Yesterday 22:40", "3 days ago", date)
    RenameProjectDialog.kt, DescribeProjectDialog.kt, KeyPickerDialog.kt, DeleteProjectDialog.kt
```

## Behaviour

- Opening a card: `session.open(id)` then `shell.select(Timeline)`; the active card shows
  "Resume". New project: `repository.create("Project N")` → open → Timeline.
- Card menu: Rename, Description, Key, Duplicate, Delete, and on the demo card "Restore demo
  project". Rename/Description/Key dialogs validate per FR-003 and edit through the session
  when the project is open (undoable) or through the repository when it is not.
- Delete confirms; deleting the open project opens the most recently edited remaining one or a
  new empty one (session).
- Search, filter and sort live in `LibraryViewStateHolder` (app-scoped) and survive leaving and
  re-entering Projects; the count reads "M of N projects" (or "N projects" with no query).
- Empty states: no projects (only possible after deleting the demo) → "No projects yet" with
  New project and Restore demo; no match → "No projects match".
- No transport bar on Projects (spec); playback continues while the user browses only if they
  chose "Keep playing" — which keeps them on Timeline, so in practice Projects is shown stopped.

## Semantics and input

Cards are `Role.Button` with a merged description (name, description, tempo, key, length,
tracks, last edited); the menu button has a tooltip; Tab traverses search → chips → sort → cards
→ menus; Enter opens the focused card; hover elevates the card (`surfaceLevel2`).

## Design conformance

Cards per `projects-library/phone` and `/tablet`: badge pills, `headlineSmall` name,
`bodySmall` description, the overview chart in the card's dark well, the stats row in
`tabularParam`, buttons as `TembraPill`. Excluded (collaboration): cloud badges, sync, export
stems, guests, the engine stat.

## Tests (first)

- `commonTest`: `ProjectsViewModelTest` (query/filter/sort combinations, count text, open and
  resume routing, dialogs, delete of the open project), `RelativeTimeTest`, `ProjectsLayoutTest`.
- `uiTest`: `ProjectsScreenTest` (six cards: demo, active, with description and key, without;
  search with and without result; menu open), `ProjectCardTest` (semantics, hover, focus),
  `ProjectOverviewChartTest`.
- Screenshots: the library at compact and expanded with at least six cards, a menu open, a
  search with results and without (spec FR-062).
