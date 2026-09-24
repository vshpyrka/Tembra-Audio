# Contract: Placeholder screen

**Module**: `:shared:ui`, package `audio.tembra.ui.placeholder` | **Entry**: `App()` in `audio.tembra.ui` wraps `TembraTheme { PlaceholderScreen() }`

```kotlin
// PlaceholderUiState.kt
data class PlaceholderUiState(val pressCount: Int = 0, val engine: EngineDescriptor)

// PlaceholderViewModel.kt — multiplatform androidx.lifecycle.ViewModel, injected AudioEngine
class PlaceholderViewModel(engine: AudioEngine) : ViewModel() {
    val uiState: StateFlow<PlaceholderUiState>
    fun onButtonPressed()            // pressCount + 1
}

// PlaceholderScreen.kt — stateless composable + a stateful wrapper that obtains the ViewModel
@Composable fun PlaceholderScreen(viewModel: PlaceholderViewModel = metroViewModel())
// measures the available width with BoxWithConstraints, maps it to WindowWidth and passes it down
@Composable fun PlaceholderScreenContent(
    state: PlaceholderUiState,
    onButtonPressed: () -> Unit,
    windowWidth: WindowWidth,
    modifier: Modifier = Modifier,
)

// WindowWidth.kt (audio.tembra.ui.theme): enum class WindowWidth { Compact, Medium, Expanded } with
// companion fun fromWidth(width: Dp): Compact below 600 dp, Medium below 840 dp, Expanded otherwise;
// val isWide get() = this != Compact. No Material window-size-class dependency.
```

**Layout**
- The layout branch is chosen from the `windowWidth` parameter (medium and expanded both
  count as wide); the root column carries `testTag("placeholder.layout.compact")` or
  `testTag("placeholder.layout.wide")`.
- Compact width: single column, page margin 16 dp, vertical spacing `spacing.lg`. Order: product
  name (`headlineLgMobile`), button row (button + counter), engine caption (`bodySm`,
  `onSurfaceVariant`), `DesignTokenPreview` filling the rest, scrollable.
- Wide width (medium and expanded): content constrained to a 720 dp centred column with
  `headlineLg`; the preview renders as a multi-column grid. Nothing stretches edge to edge.
- Safe areas / system bars respected via `Scaffold` content padding on both platforms.

**Semantics and test tags**

| Element | testTag | Semantics |
|---|---|---|
| product name | `placeholder.title` | text "Tembra Audio" |
| button | `placeholder.button` | label text "Count up", role Button, contentDescription "Count up", tooltip "Adds one to the counter", minimum 44 dp, visible hover and focus states |
| root layout | `placeholder.layout.compact` / `placeholder.layout.wide` | which layout branch rendered |
| counter | `placeholder.counter` | text "Pressed N times" with tabular figures; `stateDescription` "N" |
| engine caption | `placeholder.engineCaption` | text equal to `EngineDescriptor.rawDescription` |
| token preview | `placeholder.tokenPreview` | see design-system-api.md |

**Input**
- Tap, mouse click, keyboard (Space/Enter on focused button) all call `onButtonPressed()`.
- Hover shows the tooltip after the platform delay; the button uses Material hover overlay.
- Scroll wheel and trackpad scroll the preview.

**Tests**
- `commonTest` (`runComposeUiTest`): initial counter is 0; one click makes it 1; three clicks
  make it 3; caption shows the fake engine's description; title text is present; keyboard
  activation (`requestFocus` + Space) and hover (`performMouseInput`, tooltip text displayed)
  fire the same callback; `windowWidth = Compact` renders `placeholder.layout.compact`,
  `Expanded` renders `placeholder.layout.wide`; an integrated case through
  `PlaceholderViewModel(FakeAudioEngine())` shows `Pressed 1 times` after one click.
- `commonTest`: `PlaceholderViewModelTest` with Turbine-free `StateFlow` assertions
  (`kotlinx-coroutines-test`): initial state, increments, engine descriptor captured once.
- Manual, recorded in `quickstart.md`: live resize behaviour, large font scale, screenshots at
  compact and wide widths on Android and iOS.
