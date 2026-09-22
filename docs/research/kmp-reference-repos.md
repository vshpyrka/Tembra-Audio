# Reference Kotlin Multiplatform repositories: structural analysis

_Primary evidence for `kmp-code-architecture.md`. Four official repositories were shallow-cloned on 2026-09-17 and their Gradle settings, version catalogs and source trees inspected directly; nothing here is taken from READMEs alone. The same text was uploaded as a source to the NotebookLM notebook `6c22014f-6f99-4f7d-a0d7-aaab57fb6d29`._


This document records what the actual Gradle module graphs, version catalogs and source trees of four official Kotlin Multiplatform sample repositories look like, as inspected from a shallow clone of each repository's default branch on 2026-09-17. It is primary evidence of what JetBrains and Google ship as reference architecture.

## 1. JetBrains/kotlinconf-app (production conference app, shared Compose Multiplatform UI on Android, iOS, desktop, web)

Repository: https://github.com/JetBrains/kotlinconf-app

Gradle modules (settings.gradle.kts): `:core`, `:backend`, `:app:shared`, `:app:ui-components`, `:app:androidApp`, `:app:desktopApp`, `:app:webApp`, `:app:adminApp`. The iOS app lives in `app/iosApp` as an Xcode workspace and is not a Gradle module.

Key versions in gradle/libs.versions.toml: Kotlin 2.4.10, Compose Multiplatform 1.11.1, androidx-navigation3 (JetBrains multiplatform build) 1.1.1, Metro 1.4.0, Koin 4.2.2 (used only by the Ktor backend via koin-ktor), navigation3-browser 1.1.0 (terrakok).

Architecture observed in `app/shared/src/commonMain/kotlin/org/jetbrains/kotlinconf`:
- Packages: `di`, `flags`, `navigation`, `network`, `screens`, `storage`, `ui`, `utils`. One shared module holds data, domain and screens; `ui-components` holds the reusable design-system composables.
- Navigation: Jetpack Navigation 3 multiplatform. `navigation/NavHost.kt` imports `androidx.navigation3.runtime.entryProvider`, `androidx.navigation3.runtime.EntryProviderScope` and `androidx.navigation3.ui.NavDisplay`. Artifacts: `org.jetbrains.androidx.navigation3:navigation3-ui` and `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-navigation3`.
- Dependency injection: Metro (dev.zacsweers.metro) compile-time DI. `di/AppGraph.kt` uses `dev.zacsweers.metrox.viewmodel.ViewModelGraph`; `di/AppBindings.kt` uses `@BindingContainer`, `@ContributesTo`, `@Binds`, `@Provides`, `@SingleIn(AppScope::class)`; `di/YearGraph.kt` is a `@GraphExtension` created with `fun create(@Provides @Year year: Int): YearGraph`, i.e. a child scope per conference year. Services such as `ConferenceService`, `YearlyApi`, `ApplicationApi`, `FlagsManager` are constructor-injected with `@Inject` and scoped with `@SingleIn(AppScope::class)`. `App.kt` provides `LocalMetroViewModelFactory provides appGraph.metroViewModelFactory` so Compose screens obtain ViewModels from the Metro graph (metrox-viewmodel-compose).
- State holders: multiplatform androidx.lifecycle ViewModels (`lifecycle-viewmodel-compose`, `lifecycle-runtime-compose`).
- Persistence: `multiplatform-settings` (com.russhwolf.settings, `ObservableSettings`) behind `ApplicationStorage`/`YearlyStorage` interfaces with `*Impl` classes; `kotlinx.serialization` JSON; Okio. No SQL database in the client.
- Tests: `app/shared/src/commonTest/kotlin/org/jetbrains/kotlinconf/storage` (kotlin-test, kotlinx-coroutines-test) and `jvmTest`. Kotlin Power-Assert plugin enabled.
- iOS entry point: `app/shared/src/iosMain/kotlin/org/jetbrains/kotlinconf/main.ios.kt` exposes `fun MainViewController(): UIViewController = ComposeUIViewController(...)`; `app/iosApp/iosApp/ContentView.swift` calls `Main_iosKt.MainViewController()`. Swift is a thin shell only.
- Extra source sets: `nonAndroidMain`, `nonWebMain`, `jvmMain`, `webMain`, `wasmJsMain` (intermediate source sets for platform groups).
- Other libraries: Ktor client, Coil 3 (coil-network-ktor3), kotlinx-datetime, aboutlibraries (license screen), kmpnotifier, multiplatform-markdown-renderer.

Conclusion: JetBrains' own production app uses a small number of Gradle modules (one shared app module plus a UI-components module), Navigation 3 multiplatform, Metro for DI, multiplatform ViewModel + StateFlow, multiplatform-settings, and Compose on iOS via `ComposeUIViewController`.

## 2. android/kotlin-multiplatform-samples — Fruitties (Google sample, shared logic + Jetpack libraries, native SwiftUI on iOS)

Repository: https://github.com/android/kotlin-multiplatform-samples/tree/main/Fruitties

Gradle modules: `:androidApp`, `:shared`. iOS app in `iosApp` (Xcode project, SwiftUI).

Key versions: Kotlin 2.2.21, Room 2.8.3 (KMP, `androidx.room` Gradle plugin, KSP, `sqlite-bundled` driver), DataStore 1.1.7 (`datastore-core-okio`, `datastore-preferences-core`), Navigation 3 1.0.0-rc01 (`androidx.navigation3:navigation3-runtime`, `navigation3-ui`), `lifecycle-viewmodel-navigation3` 1.0.0-alpha04, SKIE 0.10.8 (Touchlab), Compose BOM 2025.11.00, Paging 3.3.6, Kermit logging, kotlinx-atomicfu, Okio, Ktor.

Architecture observed in `shared/src/commonMain/kotlin/com/example/fruitties`:
- Packages: `database` (`@Database AppDatabase`, Room DAOs, `CartDataStore` built with `DataStoreFactory.create`), `di`, `model`, `network`, `viewmodel` (`MainViewModel`, `FruittieViewModel`, `CartViewModel` extending the multiplatform androidx ViewModel).
- Dependency injection: manual. `di/AppContainer.kt` is a plain class that wires `DataRepository` from a `Factory`; `di/Factory.kt` is an `expect class Factory` with `actual` implementations in `androidMain` (`Factory.android.kt`, takes an Android Context) and `iosMain` (`Factory.native.kt`) that create the Room database and DataStore with platform file paths. No DI framework.
- Navigation: Navigation 3 used in the Android app only (`androidApp/.../MainActivity.kt`: `@Serializable data object ListScreenKey : NavKey`, `rememberNavBackStack(ListScreenKey)`, `NavDisplay(...)`, `entryProvider`). iOS uses SwiftUI navigation.
- iOS consumption of shared ViewModels: `iosApp/iosApp/IOSViewModelStoreOwner.swift` implements the Kotlin `ViewModelStoreOwner` protocol (exported through SKIE) with a `ViewModelStore`, a generic `viewModel<T: ViewModel>(key:factory:extras:)` helper and `clear()` in `deinit`; `ViewModelStoreOwnerProvider.swift` and `ObservableValueWrapper.swift` bridge StateFlow to SwiftUI. Screens `ContentView.swift`, `FruittieScreen.swift`, `CartView.swift`.
- Room schema JSON exported under `shared/schemas/`.

Conclusion: Google's reference shows the "shared logic, native UI" variant: one `shared` module with Room KMP + DataStore + multiplatform ViewModel, manual DI through an `expect/actual Factory`, Navigation 3 on Android, and SKIE to consume ViewModels and Flows from SwiftUI.

## 3. Kotlin/KMP-App-Template (JetBrains template, shared Compose Multiplatform UI)

Repository: https://github.com/Kotlin/KMP-App-Template

Gradle modules: `:shared`, `:androidApp`, `:desktopApp`, `:webApp`; iOS in `iosApp` (Xcode). Plugins: `com.android.kotlin.multiplatform.library` (the new Android KMP library plugin), Compose Multiplatform, Compose compiler, kotlinx-serialization.

Key versions: Koin 4.2.2 (`koin-core`, `koin-compose-viewmodel`), `org.jetbrains.androidx.navigation:navigation-compose` 2.9.2 (Navigation 2 multiplatform, not Navigation 3), `androidx-lifecycle-runtimeCompose`, Ktor, Coil, Compose resources.

Source tree: everything (data, screens, DI) inside `shared/src/commonMain`, with an `iosMain` for the `ComposeUIViewController` entry point.

Conclusion: the starter template is a single shared module, Koin for DI, multiplatform ViewModel and Navigation 2 (`navigation-compose`).

## 4. Kotlin/KMP-App-Template-Native (JetBrains template, shared logic, native UI: Compose on Android and desktop, SwiftUI on iOS, React on web)

Repository: https://github.com/kotlin/KMP-App-Template-Native

Gradle modules: `:shared` (logic), `:sharedUI` (Compose UI for Android and desktop), `:androidApp`, `:desktopApp`; `iosApp` (Xcode, SwiftUI) and `webApp` (React, npm) are not Gradle modules.

Key versions: Koin 4.2.2, `org.jetbrains.androidx.navigation:navigation-compose` 2.9.2 (in `sharedUI`), `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose`, KMP-ObservableViewModel 1.1.0 (`com.rickclephas.kmp:kmp-observableviewmodel-core`) and KMP-NativeCoroutines 1.0.6 Gradle plugin (Rick Clephas) to expose ViewModels and Flows to SwiftUI, kotlinx-serialization, Ktor, Coil.

Source tree: `shared/src/commonMain/kotlin/com/jetbrains/kmpapp/{di/Koin.kt, data/MuseumApi.kt, data/MuseumRepository.kt, data/MuseumStorage.kt, data/MuseumObject.kt, screens/ListViewModel.kt, screens/DetailViewModel.kt}`; `sharedUI/src/commonMain/kotlin/com/jetbrains/kmpapp/{App.kt, screens/ListScreen.kt, screens/DetailScreen.kt, screens/EmptyScreenContent.kt}`.

Conclusion: when the UI is native per platform, JetBrains splits `shared` (ViewModels and data, consumed by SwiftUI through KMP-ObservableViewModel/KMP-NativeCoroutines) from `sharedUI` (Compose screens for the Compose platforms).

## Cross-repository observations

1. Module count is small in all four: one shared logic module, optionally one shared UI/components module, one thin app module per platform. None of them uses a Now-in-Android-style `:core:*`/`:feature:*` fan-out.
2. Navigation: the two newest codebases (kotlinconf-app on Kotlin 2.4 / Compose Multiplatform 1.11, Fruitties on Nav3 1.0.0-rc01) use Navigation 3; the two templates still use Navigation 2 multiplatform 2.9.2.
3. State holders: all four use the multiplatform androidx.lifecycle ViewModel with StateFlow.
4. DI: Metro (kotlinconf-app), Koin (both templates), manual expect/actual factory (Fruitties). No Dagger/Hilt in shared code.
5. Persistence: Room KMP + DataStore (Fruitties); multiplatform-settings + kotlinx.serialization + Okio (kotlinconf-app); in-memory or Ktor only (templates).
6. iOS integration: Compose apps expose one `ComposeUIViewController` from `iosMain` and keep Swift to a few lines; native-UI apps use SKIE or KMP-NativeCoroutines/ObservableViewModel to consume Flows in SwiftUI.
7. Build plumbing: version catalogs everywhere; the new `com.android.kotlin.multiplatform.library` plugin in the JetBrains templates and kotlinconf-app; no build-logic convention plugin modules in any of the four (they are small enough not to need them). kotlinconf-app uses intermediate source sets (`nonAndroidMain`, `nonWebMain`, `webMain`) to share code across platform groups.
