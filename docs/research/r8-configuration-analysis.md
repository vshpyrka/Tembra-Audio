# R8 configuration: analysis against Google's app-optimization guide

Written 2026-11-05 against `main` at 7ec955a4 (after feature 010 and 011), on branch `worktree-r8-analysis`. The
question was whether the Android app's R8 setup is as well optimized as it can be. The answer came from three
sources: the Android CLI's `r8-analyzer` skill (Google, last updated 2026-10-24), AGP's R8 Configuration Analyzer
(`:androidApp:analyzeReleaseR8Config`), and the 19 pages under
[developer.android.com/topic/performance/app-optimization](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization),
read through the CLI's knowledge base (`android docs fetch kb://android/topic/performance/app-optimization/<page>`).
The iOS app is untouched by anything here.

> TL;DR — The configuration is already where the guide wants it. R8 runs in full mode with optimized resource
> shrinking, AGP's optimize defaults and a committed startup profile, and the app adds no keep rule of its own. The
> analyzer scores 98.85 % optimization, 99.18 % obfuscation and 99.19 % shrinking. Every one of the 74 keep rules
> comes from AGP's defaults or a library, and the broadest one holds 0.33 % of the live code. The guide's headline
> advice for AGP 9.3, the `optimization { enable = true }` block, can't be adopted yet: the baseline-profile plugin
> then shrinks the profile-generation build. The branch moves the rules file to AGP 9.3's `keepRules` source set and
> records why the DSL stays as it is; the shipped APK is byte-identical to `main`'s. What's left to save is outside
> R8: 17.8 MB of the 26.8 MB APK are uncompressed demo WAVs in assets, which resource shrinking never touches.

---

## 1. What was measured

| | |
|---|---|
| Build | AGP 9.3.3, R8 9.3.28 (full mode, from the DEX marker), Kotlin 2.4.21, Gradle 9.7.0, `minSdk` 26 |
| Variant | `:androidApp` `release`; `perf` is `initWith(release)` and, built without `-Ptembra.perf`, produced the same DEX bytes |
| R8 switches on `main` | `isMinifyEnabled = true`, `isShrinkResources = true`, `proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")` |
| App keep rules | none: `proguard-rules.pro` held only its policy header |
| `gradle.properties` | no `android.enableR8.fullMode`, no `android.r8.*` override |
| Profiles | committed `baseline-prof.txt` (33,388 lines) and `startup-prof.txt` (26,084 lines), so R8 also lays out the DEX for startup |

`android analyze --analysis=optimization` on the release APK confirms the marker: R8 enabled, full mode, 99.25 %
of names obfuscated, 3.90 MB of uncompressed DEX.

## 2. What the analyzer found

Of 99,986 live items (12,056 classes, 25,399 fields, 62,531 methods), 1,149 can't be optimized, 820 can't be
renamed and 809 can't be removed. Those counts are the three scores. No global option (`-dontoptimize`,
`-dontobfuscate`, `-dontshrink`, a `!` inversion) reaches the configuration from any source.

The 74 keep rules come from AGP's `proguard-android-optimize.txt` (14) and from 21 library artifacts (60). 27 of
them match nothing in this app, which costs nothing. The rules that keep the most:

| Rule | Source | Keeps | What it holds |
|---|---|---|---|
| `-keepclassmembers enum * { values(); valueOf(String); }` | AGP defaults | 328 methods, 0.33 % | `values`/`valueOf` of every enum, for `Enum.valueOf` by name |
| `-keepclassmembernames,allowobfuscation,allowshrinking class androidx.core.view.WindowInsetsCompat$*Impl* { <methods>; }` | `androidx.core:core:1.18.0` | 144 methods, 0.14 % | the insets implementations' method names |
| `-keepclassmembers public class * extends android.view.View { void set*(***); *** get*(); }` | AGP defaults | 139 methods, 0.14 % | view property setters and getters for animators |
| `-keep,allowobfuscation,allowshrinking class * extends androidx.compose.ui.node.ModifierNodeElement` | `androidx.compose.ui:ui-android:1.12.1` | 101 classes, 0.10 % | every modifier element class: renamable and removable, not optimizable |
| `-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }` | `kotlinx-coroutines-core-jvm:1.11.0` | 61 fields, 0.06 % | the atomic field updaters' targets |
| `-keepclasseswithmembernames,includedescriptorclasses class * { native <methods>; }` | AGP defaults | 3 classes, 52 methods, 0.06 % | `NativeAudioEngine` with its 51 `external` functions, and two `androidx.graphics.path` classes |
| `-if @kotlinx.serialization.Serializable class ** -keep,allowshrinking,allowoptimization,allowobfuscation,allowaccessmodification class <1>` | `kotlinx-serialization-core-jvm:1.12.0` | 36 classes, 0.04 % | the `@Serializable` classes, the project document's among them; still optimizable |

None of these is in the app's own files, so the skill's action for each is "not editable here". The analyzer also
reports two Compose `throw*Exception` rules (from `animation-core` and `foundation`) subsumed by `ui`'s wider copy,
and two identical `kotlinx.serialization` rules (its `-r8.pro` and `-common.pro` files). Both are inside the
libraries and keep a few dozen methods; neither is worth an upstream report.

## 3. The guide, page by page

| Page | What it asks for | Tembra |
|---|---|---|
| [Enable app optimization](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization) | R8 and resource shrinking on the release build, the optimize defaults, full mode, optimized resource shrinking (default from AGP 9.0), startup profiles; on AGP 9.3 the `optimization {}` block and `src/<variant>/keepRules/*.keep` | All in place except the block, which is blocked (section 4). The rules file moved to `keepRules` on this branch. |
| [Full mode](https://developer.android.com/topic/performance/app-optimization/full-mode) | No `android.enableR8.fullMode=false`; keep attributes, default constructors and Kotlin metadata that reflection needs | Full mode is on. The app reflects on nothing of its own and has no `kotlin-reflect`, so none of the full-mode keep cases applies. |
| [Optimize specified packages](https://developer.android.com/topic/performance/app-optimization/optimize-specified-packages) | `packageScope`, only for apps not yet on R8 | Not applicable; the page advises against it for apps already on R8. |
| [Configuration Analyzer](https://developer.android.com/topic/performance/app-optimization/r8-configuration-analyzer) | Track the three scores, find broad, subsumed, unused and identical rules | Section 2. The report is also written by every `assembleRelease` to `build/outputs/mapping/release/configanalyzer.html`, so the CI job that builds R8 already produces it. |
| [Choose libraries wisely](https://developer.android.com/topic/performance/app-optimization/choose-libraries-wisely) | Codegen over reflection, no library with package-wide or global rules, `ignoreFrom` for a bad one | kotlinx.serialization and Metro are codegen. No library brings a whole-package `-keep … { *; }` or a global option, and the widest library rule keeps 0.14 %. No filter needed. |
| [Adopt incrementally](https://developer.android.com/topic/performance/app-optimization/adopt-optimizations-incrementally) | `-dontobfuscate`/`-dontoptimize` and package-wide keeps as temporary steps | Not needed: feature 008 turned R8 on in full, and its device runs needed no keep rule (`specs/008-baseline-profiles/baselines.md`). |
| [Keep rules overview](https://developer.android.com/topic/performance/app-optimization/keep-rules-overview), [Add keep rules](https://developer.android.com/topic/performance/app-optimization/add-keep-rules), [Best practices](https://developer.android.com/topic/performance/app-optimization/keep-rules-best-practices), [Examples](https://developer.android.com/topic/performance/app-optimization/keep-rule-examples) | Rules only for reflection and JNI, as narrow as possible | The JNI downcalls are covered by the default `native <methods>` rule. The engine never calls back into Kotlin (`tembra_engine.h` is a plain C ABI), so the upcall rules the examples show aren't needed. The code's `::class.java` uses are `Intent` and `getSystemService` targets, which R8 sees. Serializers are named explicitly (`ProjectDocument.serializer()`). |
| [Global options](https://developer.android.com/topic/performance/app-optimization/global-options) | `-repackageclasses` (default since AGP 9.1), `-allowaccessmodification` (in the optimize defaults), `-processkotlinnullchecks`; drop legacy ProGuard options | The defaults are in effect and there are no legacy options. `-processkotlinnullchecks remove` was measured (section 6) and isn't recommended. |
| [Jetpack Keep](https://developer.android.com/topic/performance/app-optimization/jetpack-keep-annotations) | Annotate reflection and JNI targets in source; alpha | Nothing to annotate today. If the engine ever gains an upcall, `@UnconditionallyKeep` in `:shared:engine` would keep the rule beside the code, since the plugin supports KMP library modules. |
| [Additional rule types](https://developer.android.com/topic/performance/app-optimization/additional-rule-types) | `-assumevalues`, `-assumenosideeffects`, `-convertchecknotnull`, `-maximumremovedandroidloglevel` | No target. `BuildConfig.DEBUG` and `PerfBuild.PERF_ENABLED` are compile-time constants R8 already folds. The app has one `Log.w`, on an error path worth keeping, and no custom null-check helper. |
| [Target a build variant](https://developer.android.com/topic/performance/app-optimization/target-a-build-variant) | Per-flavor rules | No flavors. One file in `src/main/keepRules` serves `release` and `perf`. |
| [Customize which resources to keep](https://developer.android.com/topic/performance/app-optimization/customize-which-resources-to-keep) | `tools:keep`/`tools:discard`, `resConfigs` | No `getIdentifier` lookups, so no keep file. Compose Multiplatform resources are assets, outside resource shrinking (section 5). |
| [Test](https://developer.android.com/topic/performance/app-optimization/test-the-optimization), [Troubleshoot](https://developer.android.com/topic/performance/app-optimization/troubleshoot-the-optimization), [Troubleshooting rules](https://developer.android.com/topic/performance/app-optimization/troubleshooting-rules) | CUJs on the release build with UI Automator, staged rollouts, `mapping.txt` for retrace, `configuration.txt`, `-whyareyoukeeping`, `-checkdiscard` | The perf rounds drive four journeys with UI Automator on the shrunk `perf` build. Play releases go to internal testing first. The AAB carries `mapping.txt`. Nothing called for the debugging rules. |
| [Library optimization](https://developer.android.com/topic/performance/app-optimization/library-optimization) | Narrow consumer rules, no global options in libraries | The `:shared:*` modules publish no consumer rules and need none. AGP 9.3 lets a KMP module ship one from `src/<variant>/keepRules` without `publish = true` if that changes. |

## 4. Why the `optimization {}` block isn't used

AGP 9.3 replaces the three legacy switches with one: `optimization { enable = true }` turns on R8, optimized
resource shrinking and the default Android rules. Both the skill and the guide recommend it for AGP 9.3, and 9.3.3
fixed a bug in it where the variant didn't report `isMinifyEnabled` (issue 556343682).

On this project it breaks profile generation. The baseline-profile Gradle plugin creates `nonMinifiedRelease`, the
build `tracepulse profile generate` measures, with `initWith(release)` followed by `isMinifyEnabled = false` and
`isShrinkResources = false`. `initWith` copies the new block, and the two legacy setters don't switch it off:

| Build type | `main` (legacy switches) | `optimization { enable = true }` | branch (legacy switches, `keepRules`) |
|---|---|---|---|
| `release` DEX | 4,092,988 B | 4,092,988 B, identical bytes | 4,092,988 B, identical bytes |
| `perf` DEX | 4,092,988 B | 4,092,988 B, identical bytes | 4,092,988 B, identical bytes |
| `nonMinifiedRelease` DEX | 31,262,140 B, no mapping | **3,827,424 B, shrunk, `mapping/nonMinifiedRelease/` written** | 31,262,140 B, no mapping |

A profile generated on a shrunk build would carry rules for R8's output instead of the source classes. The
plugin's bytecode in 1.5.0 (the version in use) and in 1.6.0-alpha01 (the newest on Google Maven on 2026-11-05)
sets the legacy `isMinifyEnabled`/`isShrinkResources` on the build types it derives and never touches the
`optimization` block. The analyzer scores were the same under both DSLs, so nothing is lost by waiting.
`build.gradle.kts` now says so beside the switches.

To retry after an `androidx.benchmark` update: switch `release` to the block, build
`:androidApp:assembleNonMinifiedRelease`, and check that no `build/outputs/mapping/nonMinifiedRelease/` appears and
the DEX stays around 31 MB.

## 5. Where the size is

R8 takes the app's code from 31.3 MB of DEX (the unshrunk build) to 4.1 MB, 87 % less. Of the 5,589 classes left,
1,299 (23 %) are Tembra's. Compose `ui`, `foundation`, `material3`, `runtime` and `animation` hold 2,546 (46 %) and
the rest of AndroidX 757. Most of what remains is Kotlin libraries: stdlib 264, coroutines 241, kotlinx-datetime 171,
kotlinx.serialization 158. kotlinx-datetime's share serves one use, the Projects library's relative times.

The 26.8 MB release APK is mostly not code:

| Part | Bytes in the APK | Note |
|---|---|---|
| Demo project WAVs, `assets/composeResources/.../files/demo/` | 17,837,600, stored | four loops, uncompressed |
| Native libraries, per ABI | 2,835,840 (arm64-v8a), 2,877,896 (x86_64), stored | Play's per-ABI split delivers one |
| DEX | 2,080,607 compressed (4,092,988 raw) | R8's output |
| Fonts, `assets/composeResources/.../font/` | 650,209 compressed | Inter and Space Grotesk, three weights each |
| `resources.arsc` and `res/` | about 285,000 | after resource shrinking |

Resource shrinking only sees Android resources. Compose Multiplatform packs drawables, fonts and files as assets, so
an unused one stays in the APK and no R8 setting changes that. If install size becomes a goal, the demo audio is the
lever: a compressed format the engine can decode, or delivering the demo after install.

## 6. Changes on the branch and how they were checked

- `app/androidApp/proguard-rules.pro` moved to `app/androidApp/src/main/keepRules/tembra.keep`, which AGP 9.3 reads
  with either DSL. The header was rewritten, so git records a new file rather than a rename. `build.gradle.kts` names only the default file now.
- The file's header was out of date. It named Room (not a dependency), `MetronomeForegroundService` (now
  `PlaybackForegroundService`) and serializable navigation keys (they're plain `NavKey` classes), and it credited
  AAPT with keeping manifest components. With optimized resource shrinking R8 does that, and the merged configuration
  has no AAPT section. The header now lists what actually covers each case and asks for an analyzer run around any
  new rule.
- `build.gradle.kts` explains why the legacy switches stay (section 4). `app/README.md` and `app/perf/README.md`
  point at the new path.

Checks: `release`, `perf`, `nonMinifiedRelease` and the analyzer built with zero warnings under `--warning-mode all`.
The release and perf APKs match `main`'s in all 188 entries, and the `:androidApp` host lane (the baseline-profile
coverage test) passed. The merged configuration lists `src/main/keepRules/tembra.keep` as "Local project :androidApp".

On the Pixel 6 Pro (Android 17), `tools/release-android.sh --no-build` installed the branch's release build and
compiled it from the baseline profile. All five destinations rendered correctly in portrait (bar) and landscape
(rail). Playback ran through the engine and the tuner opened the microphone, which exercises the JNI bridge. The
log showed no crash and no `ClassNotFoundException`, `NoSuchMethodError` or `UnsatisfiedLinkError`. No perf round
was run: with the same APK it would measure `main`'s binary again.

One option was measured and reverted. `-processkotlinnullchecks remove` cuts the DEX by 60,068 bytes (1.5 %) and the
APK by 16 KB. The cost is that a `null` reaching non-null Kotlin code from the platform would fail later and
elsewhere instead of at the boundary. That trade isn't worth 16 KB.

## 7. Recommendations

1. Keep the configuration as it is and merge the branch.
2. Run `./gradlew :androidApp:analyzeReleaseR8Config` after adding a keep rule or bumping a library with consumer
   rules, and compare against section 2. A visible drop means the new rule keeps more than its symptom needs.
   Uploading `androidApp/build/outputs/mapping/release/configanalyzer.html` from the CI release job would make every
   change reviewable without a local build; that wasn't done here.
3. Revisit `optimization { enable = true }` when Renovate brings a new `androidx.benchmark`, using the check in
   section 4.
4. Leave out `packageScope`, package-wide keeps, `-processkotlinnullchecks remove` and Jetpack Keep until there's a
   case for them.
5. For install size, look at the demo assets, not R8.
