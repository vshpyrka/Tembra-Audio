# Feature 011 screenshots and evidence

## Compose compiler report (T134, 2026-11-03)

`./gradlew :shared:ui:compileAndroidMain -PcomposeReports=true --no-build-cache --rerun` on the branch after US5
(`shared/ui/build/compose_compiler/tembra_shared:ui-composables.csv`): 238 composables, 204 restartable and all of
them skippable, 34 neither (inline or value-returning: `remember…` helpers, `RegisterShortcuts`, `RegisterOverlay`,
`RegisterTextInput`, the composable `Modifier` factories `audioDropTarget` and `hoverArea`). Feature 011 adds no
restartable composable that cannot skip, so there is nothing to explain.

## Captures

- `us5-picker-roots-api37.png`: the audio picker opened from the empty timeline inside the app's free-form window on
  Tembra_Desktop_API37, offering Recent, Audio, Downloads, Android Desktop and a removable "Virtual SD card"
  (FR-025, T121).
