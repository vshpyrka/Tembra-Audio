# Contract: platform shells (Android app, iOS app)

Thin shell changes only (Principle II): declarations, OS entry points and bridges that hand platform input to common
code. Evidence: research R1–R5, R6, R9, R12.

## Android (`app/androidApp`)

`src/main/AndroidManifest.xml`:

```xml
<uses-feature android:name="android.hardware.touchscreen" android:required="false" />
<uses-feature android:name="android.hardware.microphone" android:required="false" />

<activity android:name=".MainActivity"
    android:launchMode="singleTask"                   <!-- research R22: files opened while running reuse the window -->
    android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|uiMode|density|keyboard|keyboardHidden|navigation|touchscreen"
    android:resizeableActivity="true" ...>
    <layout android:minWidth="360dp" android:minHeight="480dp" />
    <meta-data android:name="WindowManagerPreference:FreeformWindowSize" android:value="tablet" />
    <intent-filter>                                   <!-- research R22: open audio from the Files app -->
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:scheme="content" />
        <data android:mimeType="audio/wav" />
        <data android:mimeType="audio/x-wav" />
        <data android:mimeType="audio/flac" />
        <data android:mimeType="audio/mpeg" />
    </intent-filter>
    ...
</activity>
```

`MainActivity`:
- `onProvideKeyboardShortcuts(data, menu, deviceId)` appends one `KeyboardShortcutGroup` per `ShortcutScope` built
  from `ShortcutCatalogue` (labels from the catalogue, modifier Ctrl); nothing else changes in key dispatch (Compose
  handles keys; `EscapeAsBack` stays for Escape outside Compose overlays).
- Binds `AndroidDroppedFilesReader(activity)` and `AndroidMicrophoneAvailability` into the graph next to the existing
  picker and permission bindings, plus `AndroidWindowControls(activity)` (`requestFullscreenMode`) and
  `AndroidOpenedFiles` (URIs from `ACTION_VIEW` intents in `onCreate`/`onNewIntent`, read under the intent's grant).
- `ForegroundState` reports foreground from `ON_START`/`ON_STOP` (not pause).

`AndroidMicrophonePermission` reports `Unavailable` when `!packageManager.hasSystemFeature(FEATURE_MICROPHONE)` or
`AudioManager.getDevices(GET_DEVICES_INPUTS)` is empty, re-evaluated on resume.

Tests (`androidApp/src/androidTest`, desktop emulator and phones):
- `ManifestFeaturesTest`: the installed package declares touchscreen and microphone as not required (PackageManager
  `reqFeatures`) and the activity carries the ChromeOS `WindowManagerPreference:FreeformWindowSize` meta-data.
- `MinimumWindowTest` (window-behaviour.md) asserts `windowLayout.minWidth/minHeight` equal 360/480 dp.
- `ForegroundStateTest`: pausing alone (another window focused) keeps foreground true; stopping (minimise) makes it
  false; starting makes it true again.
- `OpenWithTest`: an `ACTION_VIEW` intent for a WAV `content://` URI from a test provider reaches `OpenedFiles` once,
  and the intent filter resolves for `audio/flac` and `audio/mpeg`; with the app running, a second `ACTION_VIEW` intent
  (with `FLAG_ACTIVITY_NEW_TASK`) arrives through `onNewIntent` in the same task (one task, one `MainActivity`).
- `KeyboardAttachTest`: a `CONFIG_KEYBOARD | CONFIG_NAVIGATION` change does not recreate the activity.
- `KeyboardShortcutsHelperTest`: `onProvideKeyboardShortcuts` returns one group per scope with every catalogue entry.
- `MicrophoneAvailabilityTest`: with no input devices (fake `AudioManager` seam) the status is `Unavailable`.

## iOS (`app/iosApp`)

- `ContentView.swift`: `.frame(minWidth: 360, minHeight: 480)` on the Compose view where a window can be resized
  (`WindowMinimum.frameMinimum`: none on a phone, whose window is its screen and in landscape shorter than 480 points;
  with the minimum the Compose view overflowed the screen and SwiftUI centred it, cutting the timeline's toolbar off
  above and the transport bar below, found by the owner on the iPhone 14 on 2026-11-09 during feature 012); on iPadOS
  26+ set `windowScene.sizeRestrictions?.minimumSize` to 360×480 when the scene connects.
- `iOSApp.swift`: `.commands { }` builds one `CommandMenu` per `ShortcutScope` with a `Button` per Command chord from
  `ShortcutCatalogue` (exported to Swift through the shared framework), each calling
  `ShortcutDispatcher.dispatch(action)` in Kotlin; plain keys are not menu commands (Compose receives them).
- `TrackpadPinchBridge.swift`: a `UIPinchGestureRecognizer` with `allowedTouchTypes = [.indirectPointer]` on the
  hosting view controller's view, forwarding scale and location to the Kotlin `IosTrackpadPinchSource`; deleted when
  the project moves to Compose Multiplatform 1.13 (research R9).
- `IosMicrophonePermission` reports `Unavailable` when `AVAudioSession.isInputAvailable` is false.
- `Info.plist`: `CFBundleDocumentTypes` for `com.microsoft.waveform-audio`, `public.mp3` and FLAC (`org.xiph.flac`, with
  a `UTImportedTypeDeclarations` entry if iOS 16 does not declare it), `LSHandlerRank` Alternate,
  `LSSupportsOpeningDocumentsInPlace` false; `iOSApp.swift` forwards `.onOpenURL` to `IosOpenedFiles`.
- A debug-only launch argument `-tembra.reportWindowMinimum` makes the shell expose the scene's
  `sizeRestrictions.minimumSize` as an accessibility value for the desktop tests (never in release builds).
- The iOS foreground source follows `ON_START`/`ON_STOP` (`didEnterBackground`), not resign-active.
- `IosDroppedFilesReader` (Kotlin, `iosMain`): reads `event.items` (`UIDragItem.itemProvider`) with
  `loadFileRepresentationForTypeIdentifier("public.audio")`, copying each file into the cache directory inside the
  completion handler; a folder item (`public.folder`) is read per the spike in research R12.

Tests (`TembraDesktopTests` XCUITest target on an iPad simulator, plus `iosTest`):
- A Command chord from the menu reaches `ShortcutDispatcher` exactly once (and is not also handled by Compose).
- `typeKey("1", modifierFlags: .command)` opens Timeline; `typeKey(.escape)` closes the shortcut sheet.
- `IosMicrophonePermissionTest`: `isInputAvailable == false` maps to `Unavailable` (fake session seam).
- `WindowMinimumTests.swift` (TembraDesktopTests, iPadOS 26+ simulator): with `-tembra.reportWindowMinimum`, the reported
  minimum is 360 × 480.
- `IosForegroundTest` (iosTest): resign-active alone keeps foreground true; background makes it false.
- `IosOpenedFilesTest` (iosTest): a file URL handed to `IosOpenedFiles` produces one opened file.
