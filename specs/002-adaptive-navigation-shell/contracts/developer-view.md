# Contract: Developer-only views (FR-022)

**Module**: `:shared:ui`, package `audio.tembra.ui.devtools` | **Callers**: `MainActivity` (Android), `MainViewController` (iOS) | **Consumers**: `App`

```kotlin
// DeveloperView.kt
enum class DeveloperView(val argument: String) { TokenPreview("token-preview") }

// DeveloperViewRequest.kt
/** What the platform shell asked to show instead of the navigation shell. Null in release builds and when no argument was given. */
data class DeveloperViewRequest(val view: DeveloperView?) {
    companion object {
        /** Maps a launch argument value ("token-preview") to a request; unknown values map to null. */
        fun parse(value: String?): DeveloperViewRequest
    }
}

// DesignTokenPreview.kt — moved unchanged from audio.tembra.ui.theme
@Composable fun DesignTokenPreview(modifier: Modifier = Modifier)

// DeveloperViewScreen.kt
/** Full-screen host for a developer view inside TembraTheme with page margins and scrolling. testTag "devtools.tokenPreview". */
@Composable fun DeveloperViewScreen(view: DeveloperView, modifier: Modifier = Modifier)

// App.kt (audio.tembra.ui)
@Composable fun App(appGraph: AppGraph, developerView: DeveloperViewRequest = DeveloperViewRequest(null))
// provides LocalMetroViewModelFactory, then TembraTheme { AppContent(developerView) { NavigationShell() } }

// AppContent.kt (audio.tembra.ui) — graph-free branch so it can be tested without Metro
@Composable fun AppContent(developerView: DeveloperViewRequest, shell: @Composable () -> Unit)
// developerView.view != null  -> DeveloperViewScreen(view)
// otherwise                    -> shell()
```

**Platform launch contract**

| Platform | Where the argument is read | Debug gate | Command |
|---|---|---|---|
| Android | `MainActivity`: `intent.getStringExtra("tembra.developer.view")` | `applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0`; the read is inside an `if (BuildConfig.DEBUG)` block so R8 removes it from release | `adb shell am start -n audio.tembra.app/audio.tembra.android.MainActivity --es tembra.developer.view token-preview` |
| iOS | Swift `ContentView` reads `CommandLine.arguments` for `--tembra-developer-view=<value>` and passes it to `MainViewController(developerView:)` | the read is inside `#if DEBUG` in Swift; Release passes `nil` | `xcrun simctl launch "iPhone 17" audio.tembra.app --tembra-developer-view=token-preview` |

`MainViewController(developerView: String? = null)` keeps the existing no-argument call working
for the Swift shell.

**Rules**
- No control, gesture, menu, deep link or long-press opens a developer view (FR-022).
- The composable code stays in the shared module on both platforms; only the entry point is
  debug-gated. On Android the release build's R8 pass removes the unreferenced branch; on iOS
  the framework still contains the composable but nothing can invoke it. Recorded in
  plan.md Complexity Tracking.
- `:androidApp` enables `buildFeatures.buildConfig = true` for the `BuildConfig.DEBUG` gate.

**Tests**
- `commonTest`: `DeveloperViewRequestTest` — `parse("token-preview")` is `TokenPreview`,
  `parse(null)` and `parse("nonsense")` are `null`.
- `uiTest`: `DesignTokenPreviewTest` (moved, unchanged assertions: 47 + 18 swatches, 12 type
  samples); `AppContentTest` — `AppContent(DeveloperViewRequest(TokenPreview)) { Text("shell") }`
  shows `devtools.tokenPreview` and not "shell"; `AppContent(DeveloperViewRequest(null))` shows "shell".
