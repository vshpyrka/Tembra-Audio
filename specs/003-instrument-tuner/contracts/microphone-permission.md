# Contract: Microphone permission

**Interface**: `:shared:ui` `commonMain`, package `audio.tembra.ui.platform` | **Android impl**: `:androidApp` | **iOS impl**: `:shared:ui` `iosMain` | **Research**: R6 | **Spec**: FR-014, FR-015

```kotlin
enum class MicrophonePermissionStatus { NotDetermined, Granted, DeniedCanAsk, DeniedOpenSettings }
interface MicrophonePermission {
    val status: StateFlow<MicrophonePermissionStatus>
    suspend fun request(): MicrophonePermissionStatus      // shows the OS prompt when the OS allows; resolves with the new status
    fun refresh()                                           // re-reads the OS state; called on every foreground(true)
    fun openSystemSettings()                                // the app's page in the system settings
}
// AppGraph: val microphonePermission: MicrophonePermission

// :androidApp
class MicrophonePermissionHost {                            // owned by AndroidAppGraph (@SingleIn); MainActivity attaches in onCreate, detaches in onDestroy
    fun attach(activity: ComponentActivity, launcher: ActivityResultLauncher<String>); fun detach()
}
class AndroidMicrophonePermission(context: Context, host: MicrophonePermissionHost) : MicrophonePermission
//   refresh(): checkSelfPermission(RECORD_AUDIO) → Granted, else NotDetermined until the first request, then
//   DeniedCanAsk when activity.shouldShowRequestPermissionRationale(RECORD_AUDIO), else DeniedOpenSettings
//   request(): launcher.launch(RECORD_AUDIO); suspends on a CompletableDeferred completed by the result callback
//   openSystemSettings(): Settings.ACTION_APPLICATION_DETAILS_SETTINGS with package: URI, FLAG_ACTIVITY_NEW_TASK
// MainActivity: registerForActivityResult(ActivityResultContracts.RequestPermission()) { host.onResult(it) } in onCreate, before setContent
// AndroidManifest: <uses-permission android:name="android.permission.RECORD_AUDIO" />

// :shared:ui iosMain
class IosMicrophonePermission : MicrophonePermission
//   refresh(): AVAudioSession.sharedInstance().recordPermission → undetermined/granted/denied(→ DeniedOpenSettings)
//   request(): requestRecordPermission { granted -> } bridged with suspendCancellableCoroutine, resumed on the main thread
//   openSystemSettings(): UIApplication.sharedApplication.openURL(NSURL(string = UIApplicationOpenSettingsURLString))
// Info.plist: NSMicrophoneUsageDescription = "Tembra Audio listens to your instrument to show its tuning."
```

**Denied state UI** (`MicrophoneDeniedState`, `tuner` package): `MicOff` icon 40 dp `onSurfaceVariant`,
title "Microphone needed" (`headlineSmall`), the FR-014 sentence plus "Allow the microphone to use
Auto Listen." (`bodyMedium`, `onSurfaceVariant`, centred, max 360 dp), one filled button "Allow
microphone" (`primaryContainer`), 44 dp, testTag `tuner.denied.action`. The ViewModel decides whether
the button requests or opens settings; the label is the same in both cases.

**Tests**: `FakeMicrophonePermission` (`commonTest`): scripted `request()` results, counts of
`request`, `refresh` and `openSystemSettings`; `TunerViewModelTest` covers the flow; `TunerScreenTest`
covers the denied panel. Platform implementations have automated tests (Constitution III, analysis C1): `AndroidMicrophonePermissionTest`
in `app/androidApp/src/androidTest/kotlin/audio/tembra/android/` (the instrumentation runner grants
`RECORD_AUDIO`, so `refresh()` yields `Granted` and `status.value` follows; `openSystemSettings()` is
called with a `ContextWrapper` that records `startActivity` and the intent's action is
`ACTION_APPLICATION_DETAILS_SETTINGS` with data `package:audio.tembra.app` and `FLAG_ACTIVITY_NEW_TASK`;
`MicrophonePermissionHost.onResult(true)` completes a pending `request()` with `Granted`) and
`IosMicrophonePermissionTest` in `app/shared/ui/src/iosTest/kotlin/audio/tembra/ui/platform/` (`refresh()`
yields one of the four statuses and `status.value` equals it; `request()` resolves within 5 s on the
simulator, where the prompt is auto-answered by `simctl privacy`). The prompts themselves are verified
manually (quickstart §6): reset, deny, re-request, permanently deny, grant in settings, revoke while listening.
