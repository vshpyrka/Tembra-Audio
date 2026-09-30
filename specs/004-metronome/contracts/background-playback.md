# Contract: Background playback, calls and other apps

**Modules**: `:shared:ui` (`audio.tembra.ui.platform.BackgroundPlaybackHost`), `:androidApp` (`MetronomeForegroundService`, `AndroidBackgroundPlaybackHost`), `:shared:ui` `iosMain` (`IosBackgroundPlaybackHost`), `:shared:engine` actuals (focus, session, events: contracts/metronome-audio-kotlin.md) | **Research**: R6 (official guidance quoted there) | **Spec**: FR-027, User Story 6

```kotlin
interface BackgroundPlaybackHost {
    /** Makes the process eligible to keep playing after the screen locks or the app is backgrounded. Returns false when the platform refuses. */
    fun keepAlive(): Boolean
    fun release()
    /** The user asked to stop from outside the app (the Android notification's Stop action). Hot, one collector (the ViewModel); a request with no collector is dropped. */
    val stopRequests: Flow<Unit>
}
class FakeBackgroundPlaybackHost(var allow: Boolean = true) : BackgroundPlaybackHost   // commonTest; `alive` flag, `requestStop()` emits
```

## Android (`:androidApp`)

- `MetronomeForegroundService : Service` — an OS entry point, holds no engine state.
  `onStartCommand(ACTION_START)`: creates the channel `metronome` (importance low, name
  "Metronome"), builds an ongoing notification (small icon `ic_stat_metronome`, title
  "Metronome", text "Running", a "Stop" action with a `PendingIntent` for `ACTION_STOP`, content
  intent opening `MainActivity`) and calls
  `startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)`
  (the three-argument form on API 29+, two-argument below). `ACTION_STOP`: publishes to
  `AndroidBackgroundPlaybackHost.stopRequests` through a process-wide `MutableSharedFlow(extraBufferCapacity = 1)`
  held by the host, then `stopForeground(STOP_FOREGROUND_REMOVE)` + `stopSelf()`. `onTaskRemoved`:
  stops itself (the engine stops when the process dies). Returns `START_NOT_STICKY`.
- Manifest: `<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>`,
  `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"/>`,
  `<service android:name=".MetronomeForegroundService" android:foregroundServiceType="mediaPlayback" android:exported="false"/>`.
  `<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>` is declared but never
  requested at runtime (B12 ruling, 2026-09-30): on Android 13+ the notification and its Stop action
  appear only once the user allows notifications for the app in system settings (without the
  declaration the system could never grant it); the service, the in-app button and the system's
  active-app chip work regardless. Device checks grant it with `adb shell pm grant`.
- `AndroidBackgroundPlaybackHost(context)`: `keepAlive()` = `ContextCompat.startForegroundService(context, Intent(ACTION_START))`
  wrapped in a `try` returning false on `ForegroundServiceStartNotAllowedException` (API 31+,
  the app is not visible); `release()` = `context.stopService(Intent(context, MetronomeForegroundService::class.java))` (not `startService(ACTION_STOP)`: starting a service from the background throws on API 26+, and an ACTION_STOP would emit a stop request the ViewModel did not ask for).
- Focus (`AUDIOFOCUS_GAIN`), the becoming-noisy receiver and the resulting `AudioEvent`s live in
  the engine actual (contracts/metronome-audio-kotlin.md). Order in the ViewModel: `keepAlive()`
  before `startMetronome()` (so the Android 15+ focus rule "top app or running a foreground
  service" holds when the user has just pressed play and the service is up), `stopMetronome()`
  before `release()`.

## iOS (`:shared:ui` `iosMain`)

- `Info.plist`: `UIBackgroundModes` = `[audio]`.
- `IosBackgroundPlaybackHost`: `keepAlive()` returns `true`; `release()` no-op; `stopRequests`
  is `emptyFlow()`. The `.playback` session activated by the engine actual keeps the app running
  while the engine renders (silence between ticks included).
- Interruptions and route changes are observed by `IosAudioSession` (feature 003) and mapped to
  `AudioEvent`s per contracts/metronome-audio-kotlin.md; the `.ended` / `shouldResume` case is
  deliberately ignored by the metronome.

## Behaviour table (the ViewModel's `PlaybackPolicy`, data-model.md §7)

| Trigger | Android source | iOS source | Result |
|---|---|---|---|
| Play pressed | `keepAlive()` → `startMetronome()` | `keepAlive()` (true) → `startMetronome()` | `Running`, or `Stopped` + `FocusDenied` (focus denied: `startMetronome()` returned `Error(AudioResult.FOCUS_DENIED)`, Android only) / `DeviceUnavailable` (host refused: `keepAlive()` false) / `DeviceUnavailable` (any other engine error) |
| Pause pressed / leaving the destination / ViewModel cleared | `stopMetronome()` → `release()` | same | `Stopped` |
| Incoming call, assistant, another app playing | `AudioEvent.Interrupted` (focus loss) | `AudioEvent.Interrupted` (`.began`) | `Stopped`; no auto-resume |
| Headphones unplugged (wired, USB-C, Bluetooth, AirPlay) | `AudioEvent.OutputRouteLost` (becoming noisy) | `AudioEvent.OutputRouteLost` (route change `.oldDeviceUnavailable` unless the previous route was only the built-in speaker or receiver) | `Stopped` |
| Headphones plugged in | (Oboe reopen inside the engine) | (RemoteIO follows) | keeps `Running` |
| Notification Stop | `stopRequests` | — | `Stopped` |
| Stream lost and not reopened | `AudioEvent.DeviceLost` (audio state fell to `Idle` while `Running`, from the 10 Hz poll) | same | `Stopped` |

**Tests**: `AndroidBackgroundPlaybackHostTest` (`androidTest`, emulator: after `keepAlive()` the
service is running in the foreground with type `mediaPlayback` (`ActivityManager` running-services
query on API < 26 is unavailable, so the test binds a `ServiceTestRule` and asserts
`foregroundServiceType`); `release()` stops it; sending `ACTION_STOP` emits one `stopRequests`
item); `IosBackgroundPlaybackHostTest` (`iosTest`: `keepAlive()` is true; `NSBundle.mainBundle`'s
`UIBackgroundModes` contains `audio`); `PlaybackPolicyTest` (`commonTest`: every row above and
that nothing restarts). Manual (quickstart §5–§7): lock screen ten minutes, app switch, a real
call, YouTube, headphones in and out, Bluetooth, the notification's Stop.
