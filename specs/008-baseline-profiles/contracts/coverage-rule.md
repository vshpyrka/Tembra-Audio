# Contract: the coverage rule and its check

## File: `app/androidApp/baseline-profile-coverage.txt`

One entry per line: `<label> <descriptor>`, separated by one or more spaces. Blank lines and lines starting with `#`
are ignored. `descriptor` is a JVM class descriptor exactly as the profile format writes it (`L` + slashed binary
name + `;`). Labels are free identifiers used only in messages.

Content (the startup path and the primary destinations the journeys visit):

```
# Startup path (app start, the activity, the navigation shell)
app.start    Laudio/tembra/android/TembraApplication;
app.activity Laudio/tembra/android/MainActivity;
shell        Laudio/tembra/ui/navigation/NavigationShellKt;
# Primary destinations the journeys visit
timeline     Laudio/tembra/ui/timeline/TimelineScreenKt;
tuner        Laudio/tembra/ui/tuner/TunerScreenKt;
metronome    Laudio/tembra/ui/metronome/MetronomeScreenKt;
```

Until 2026-10-17 the first destination line was `home         Laudio/tembra/ui/home/HomeScreenKt;`. Home was
removed from the app that day and Timeline, the start destination, took its place on the startup path, so the
`home` rule was replaced by the `timeline` rule and the profiles are regenerated with it (FR-012).

A later feature that adds a primary destination the journeys visit appends a line and regenerates the profile in the
same feature (spec FR-012). Extra coverage in the profile never fails the check.

## Check: `BaselineProfileCoverageTest` (`app/androidApp/src/test/kotlin/audio/tembra/android/`)

- Reads `baseline-profile-coverage.txt` and `src/main/generated/baselineProfiles/baseline-prof.txt` relative to the
  module directory (the working directory of Gradle unit tests).
- Fails with "baseline profile missing: run `uv run tracepulse profile generate --device <id>`" when the profile file
  does not exist or has no rule line.
- A descriptor is covered when some rule line equals the descriptor or starts with the descriptor followed by `->`,
  ignoring the `H`, `S`, `P` flag prefix.
- Fails listing every uncovered `label (descriptor)` in rule-file order; passes otherwise.
- The pure logic (`BaselineProfileCoverage.uncovered(rules, profileLines)`) is a separate type in its own file and is
  what the test exercises first (red: empty profile → all labels uncovered).
- Joins the host lane: the `tembra.android.app` convention registers `checkAndroidHost` depending on
  `testDebugUnitTest`, and the root `checkAndroidHost` lane adds `:androidApp:checkAndroidHost`.
- `tracepulse profile generate` runs this test after generation (contracts/cli.md).
