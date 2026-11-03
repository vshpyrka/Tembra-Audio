# Contract: platform shells (Android app, iOS app)

Thin shell changes only (Principle II): declarations, OS entry points and bridges that hand platform input to common
code. Evidence: research R1–R5, R6, R9, R12.

## Android (`app/androidApp`)

`src/main/AndroidManifest.xml`:

```xml
<uses-feature android:name="android.hardware.touchscreen" android:required="false" />
<uses-feature android:name="android.hardware.microphone" android:required="false" />

<activity android:name=".MainActivity"
    android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|uiMode|density|keyboard|keyboardHidden|navigation|touchscreen"
    android:resizeableActivity="true" ...>
    <layout android:minWidth="360dp" android:minHeight="480dp" />
    <meta-data android:name="WindowManagerPreference:FreeformWindowSize" android:value="tablet" />
    ...
</activity>
```

`MainActivity`:
- `onProvideKeyboardShortcuts(data, menu, deviceId)` appends one `KeyboardShortcutGroup` per `ShortcutScope` built
  from `ShortcutCatalogue` (labels from the catalogue, modifier Ctrl); nothing else changes in key dispatch (Compose
  handles keys; `EscapeAsBack` stays for Escape outside Compose overlays).
- Binds `AndroidDroppedFilesReader(activity)` and `AndroidMicrophoneAvailability` into the graph next to the existing
  picker and permission bindings.

`AndroidMicrophonePermission` reports `Unavailable` when `!packageManager.hasSystemFeature(FEATURE_MICROPHONE)` or
`AudioManager.getDevices(GET_DEVICES_INPUTS)` is empty, re-evaluated on resume.

Tests (`androidApp/src/androidTest`, desktop emulator and phones):
- `ManifestFeaturesTest`: the installed package declares touchscreen and microphone as not required (PackageManager
  `reqFeatures`), the activity's `windowLayout.minWidth/minHeight` equal 360/480 dp.
- `KeyboardAttachTest`: a `CONFIG_KEYBOARD | CONFIG_NAVIGATION` change does not recreate the activity.
- `KeyboardShortcutsHelperTest`: `onProvideKeyboardShortcuts` returns one group per scope with every catalogue entry.
- `MicrophoneAvailabilityTest`: with no input devices (fake `AudioManager` seam) the status is `Unavailable`.

## iOS (`app/iosApp`)

- `ContentView.swift`: `.frame(minWidth: 360, minHeight: 480)` on the Compose view; on iPadOS 26+ set
  `windowScene.sizeRestrictions?.minimumSize` to 360×480 when the scene connects.
- `iOSApp.swift`: `.commands { }` builds one `CommandMenu` per `ShortcutScope` with a `Button` per Command chord from
  `ShortcutCatalogue` (exported to Swift through the shared framework), each calling
  `ShortcutDispatcher.dispatch(action)` in Kotlin; plain keys are not menu commands (Compose receives them).
- `TrackpadPinchBridge.swift`: a `UIPinchGestureRecognizer` with `allowedTouchTypes = [.indirectPointer]` on the
  hosting view controller's view, forwarding scale and location to the Kotlin `IosTrackpadPinchSource`; deleted when
  the project moves to Compose Multiplatform 1.13 (research R9).
- `IosMicrophonePermission` reports `Unavailable` when `AVAudioSession.isInputAvailable` is false.
- `IosDroppedFilesReader` (Kotlin, `iosMain`): reads `event.items` (`UIDragItem.itemProvider`) with
  `loadFileRepresentationForTypeIdentifier("public.audio")`, copying each file into the cache directory inside the
  completion handler; a folder item (`public.folder`) is read per the spike in research R12.

Tests (`TembraDesktopTests` XCUITest target on an iPad simulator, plus `iosTest`):
- A Command chord from the menu reaches `ShortcutDispatcher` exactly once (and is not also handled by Compose).
- `typeKey("1", modifierFlags: .command)` opens Timeline; `typeKey(.escape)` closes the shortcut sheet.
- `IosMicrophonePermissionTest`: `isInputAvailable == false` maps to `Unavailable` (fake session seam).
