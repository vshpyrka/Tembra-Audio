# Third-party licences

Every dependency of the application, with its licence (Constitution VI: permissive licences only in the
shipped binary). Versions are pinned in `gradle/libs.versions.toml` unless a path is given.
Last reviewed: 2026-09-29 (feature 004, metronome).

## Shipped in the app binaries

| Dependency | Version | Licence | Source |
|---|---|---|---|
| Kotlin standard library, kotlinx-coroutines | 2.4.20, 1.11.0 | Apache-2.0 | https://github.com/JetBrains/kotlin, https://github.com/Kotlin/kotlinx.coroutines |
| Compose Multiplatform (runtime, foundation, material3, ui, components-resources) | 1.12.1 | Apache-2.0 | https://github.com/JetBrains/compose-multiplatform |
| Jetpack Compose (Android artifacts used by Compose Multiplatform) | 1.12.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx |
| AndroidX Lifecycle multiplatform (`lifecycle-viewmodel-compose`, `lifecycle-runtime-compose`) | 2.11.0 | Apache-2.0 | https://github.com/JetBrains/compose-multiplatform-core |
| AndroidX Activity Compose | 1.13.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/activity |
| Navigation 3 multiplatform (`org.jetbrains.androidx.navigation3:navigation3-ui`) | 1.1.2 | Apache-2.0 | https://github.com/JetBrains/compose-multiplatform-core |
| Navigation 3 runtime (`androidx.navigation3:navigation3-runtime`, transitive) | 1.1.7 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/navigation3 |
| AndroidX Lifecycle multiplatform (`lifecycle-viewmodel-navigation3`) | 2.11.0 | Apache-2.0 | https://github.com/JetBrains/compose-multiplatform-core |
| Material 3 adaptive (`org.jetbrains.compose.material3.adaptive:adaptive`) | 1.2.0 | Apache-2.0 | https://github.com/JetBrains/compose-multiplatform-core |
| Material 3 adaptive navigation suite (`org.jetbrains.compose.material3:material3-adaptive-navigation-suite`) | 1.9.0 | Apache-2.0 | https://github.com/JetBrains/compose-multiplatform-core |
| AndroidX Window core (`androidx.window:window-core`, `org.jetbrains.androidx.window:window-core`, transitive) | 1.4.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/window |
| Material Symbols Outlined glyphs (`shared/ui/src/commonMain/composeResources/drawable/ic_*_24.xml`) | google/material-design-icons@27e9ef1dbeedc13d682fece4a58e1eda4cb0961a (master, 2026-09-18; no usable release tag) | Apache-2.0 (`composeResources/files/LICENSE-MaterialSymbols.txt`) | https://github.com/google/material-design-icons |
| Metro (compiler plugin runtime, `metrox-viewmodel-compose`) | 1.4.4 | Apache-2.0 | https://github.com/ZacSweers/metro |
| AndroidX DataStore Preferences (`androidx.datastore:datastore-preferences-core`, with `datastore-core-okio`) | 1.2.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/datastore |
| Okio (`com.squareup.okio:okio`, direct dependency of `:shared:data`; `okio-fakefilesystem` in tests only) | 3.9.1 | Apache-2.0 | https://github.com/square/okio |
| Oboe (Android only, via Prefab) | 1.11.0 | Apache-2.0 | https://github.com/google/oboe |
| miniaudio (`engine/third_party/miniaudio`) | 0.11.25 | MIT-0 or public domain (dual) | https://github.com/mackron/miniaudio |
| dr_wav from dr_libs (`engine/third_party/dr_libs`) | 0.14.5 | MIT-0 or public domain (dual) | https://github.com/mackron/dr_libs |
| Signalsmith Stretch with its `dsp/` headers (`engine/third_party/signalsmith-stretch`) | 1.1.0 | MIT | https://github.com/Signalsmith-Audio/signalsmith-stretch |
| Space Grotesk (`shared/ui/src/commonMain/composeResources/font/space_grotesk_*.ttf`) | 2.0.0 (Medium and Bold static files; SemiBold instanced from the variable font with fontTools) | SIL Open Font License 1.1 (`composeResources/files/OFL-SpaceGrotesk.txt`) | https://github.com/floriankarsten/space-grotesk |
| Inter (`shared/ui/src/commonMain/composeResources/font/inter_*.ttf`) | 4.x variable font from google/fonts, Regular/Medium/SemiBold instanced with fontTools | SIL Open Font License 1.1 (`composeResources/files/OFL-Inter.txt`) | https://github.com/rsms/inter |
| Android NDK libc++ shared runtime (`libc++_shared.so`) | NDK 29.0.14206865 | Apache-2.0 with LLVM exceptions | https://developer.android.com/ndk |

## Test-only

| Dependency | Version | Licence | Source |
|---|---|---|---|
| kotlin-test, kotlinx-coroutines-test | 2.4.20, 1.11.0 | Apache-2.0 | JetBrains |
| kotlinx-serialization-json (theme parity test on the JVM) | 1.11.0 | Apache-2.0 | https://github.com/Kotlin/kotlinx.serialization |
| Compose UI test (`org.jetbrains.compose.ui:ui-test`, `ui-test-junit4-android`, `ui-test-manifest`) | 1.12.1 | Apache-2.0 | JetBrains, AndroidX |
| AndroidX Test runner, rules, Espresso core | 1.7.0, 1.7.0, 3.7.0 | Apache-2.0 | https://developer.android.com/testing |
| Catch2 (fetched by CMake for `engine/tests`) | v3.16.0 | Boost Software License 1.0 | https://github.com/catchorg/Catch2 |

## Build tooling (not shipped)

| Tool | Version | Licence |
|---|---|---|
| Gradle (wrapper) | 9.7.0 | Apache-2.0 |
| Android Gradle Plugin | 9.3.3 | Apache-2.0 |
| Kotlin Gradle Plugin, Compose Multiplatform Gradle plugin, Metro Gradle plugin | 2.4.20, 1.12.1, 1.4.4 | Apache-2.0 |
| CMake (Android SDK package), Ninja | 3.31.6 | BSD-3-Clause, Apache-2.0 |
| Android NDK | 29.0.14206865 | Apache-2.0 / NDK licence |
| fontTools (used once to instance the static font files) | 4.x | MIT |

## Attribution

- `iosApp/` was derived from Kotlin/KMP-App-Template (JetBrains, Apache-2.0): https://github.com/Kotlin/KMP-App-Template
- The Android launcher glyph and the iOS app icon are original placeholder artwork in the project's colours.
