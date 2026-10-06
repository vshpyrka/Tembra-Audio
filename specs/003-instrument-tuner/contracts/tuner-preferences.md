# Contract: Tuner preferences (`:shared:data`)

> Extended by feature 006 ([contract](../../006-tuner-noise-gate/contracts/tuner-preferences.md)): the remembered noise gate level, key `tuner.noiseGateDb`.

**Package**: `audio.tembra.data.tuner` | **Depends on**: `:shared:core`, DataStore Preferences 1.2.1 (research R10) | **Consumers**: `TunerViewModel`

```kotlin
data class TunerPreferencesSnapshot(
    val instrument: Instrument = Instrument.Guitar,
    val guitarStrings: GuitarStringCount = GuitarStringCount.Six,
    val presetId: String = "guitar6-standard",
    val mode: TunerMode = TunerMode.AutoListen,
    val loopTone: Boolean = true,
)
interface TunerPreferences {
    val snapshot: Flow<TunerPreferencesSnapshot>                          // emits the current value first, then every change
    suspend fun update(transform: (TunerPreferencesSnapshot) -> TunerPreferencesSnapshot)
}
class DataStoreTunerPreferences(private val dataStore: DataStore<Preferences>) : TunerPreferences   // keys: tuner.instrument, tuner.guitarStrings, tuner.presetId, tuner.mode, tuner.loopTone
class FakeTunerPreferences(initial: TunerPreferencesSnapshot = TunerPreferencesSnapshot()) : TunerPreferences   // in-memory MutableStateFlow; commonMain like FakeAudioEngine
object TembraDataStore { fun preferences(directory: String): DataStore<Preferences> }   // PreferenceDataStoreFactory.createWithPath { "$directory/tembra.preferences_pb".toPath() }; one instance per process (SingleIn)

interface AppDirectories { val documents: String; val cache: String; val preferences: String }   // preferences added: Android filesDir/datastore, iOS Library/Application Support/datastore
```

**Wiring**: `AppBindings` provides `TunerPreferences` as `DataStoreTunerPreferences(TembraDataStore.preferences(appDirectories.preferences))`,
`@SingleIn(AppScope::class)`. `AppGraph` exposes `val tunerPreferences: TunerPreferences`.

**Decoding rules**: an absent or unknown `instrument`, `guitarStrings` or `mode` yields the field's
default; `presetId` is stored verbatim and validated by the ViewModel against `TuningPresets`
(a preset that does not belong to the stored instrument and string count is replaced by that pair's default).

**Tests**: `TunerPreferencesSnapshotTest` and `FakeTunerPreferencesTest` (`commonTest`);
`DataStoreTunerPreferencesTest` in `androidDeviceTest` and `iosTest`: write, read back, reopen the
file and read again; a corrupted value string yields the default. `AppDirectoriesTest` (existing,
both platforms) asserts `preferences` exists and is writable.
