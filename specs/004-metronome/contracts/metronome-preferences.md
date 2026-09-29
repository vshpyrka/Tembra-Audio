# Contract: Metronome preferences and the custom sound store (`:shared:data`)

**Package**: `audio.tembra.data.metronome` | **Depends on**: `:shared:core`, DataStore Preferences 1.2.1, Okio (direct) | **Consumers**: `MetronomeViewModel`

```kotlin
data class MetronomePreferencesSnapshot(val settings: MetronomeSettings = MetronomeSettings())   // encode()/decode() per data-model.md §5
interface MetronomePreferences {
    val snapshot: Flow<MetronomePreferencesSnapshot>                                       // current value first, then every change
    suspend fun update(transform: (MetronomePreferencesSnapshot) -> MetronomePreferencesSnapshot)
}
class DataStoreMetronomePreferences(private val dataStore: DataStore<Preferences>) : MetronomePreferences   // keys: MetronomePreferenceKeys (8 string keys)
class FakeMetronomePreferences(initial: MetronomePreferencesSnapshot = MetronomePreferencesSnapshot()) : MetronomePreferences   // in-memory, commonMain

interface CustomClickSoundStore {                       // data-model.md §5
    val path: String; val stagingPath: String
    suspend fun commit(): Boolean; suspend fun exists(): Boolean; suspend fun delete()
}
class OkioCustomClickSoundStore(directories: AppDirectories, private val fileSystem: FileSystem = FileSystem.SYSTEM) : CustomClickSoundStore
//   directory <documents>/metronome created on first use; commit() = atomicMove(staging → path) after deleting an existing target; runs on Dispatchers.IO inside
class FakeCustomClickSoundStore : CustomClickSoundStore   // commonMain; records calls, `present` flag

object TembraDataStore { fun preferences(directory: String, scope: CoroutineScope): DataStore<Preferences> }   // unchanged signature; tunerPreferences() removed
```

**Wiring** (`AppBindings`): one `@Provides @SingleIn(AppScope::class) fun providePreferencesDataStore(directories, @Named("app") scope): DataStore<Preferences>`;
`provideTunerPreferences(dataStore) = DataStoreTunerPreferences(dataStore)` (was built through
`TembraDataStore.tunerPreferences`; behaviour unchanged, `DataStoreTunerPreferencesTest`
unchanged); `provideMetronomePreferences(dataStore) = DataStoreMetronomePreferences(dataStore)`;
`provideCustomClickSoundStore(directories) = OkioCustomClickSoundStore(directories)`, all
`@SingleIn(AppScope::class)`. `AppGraph` exposes `metronomePreferences` and `customClickSoundStore`.

**Decoding rules**: an absent or unknown `signature`, `subdivision`, `style` or `sound` id yields
the default; `tempo` that is not an integer in 40–240 yields 120; `levels` is decoded against the
decoded signature (bad char → the style's pattern or Standard; wrong length → `resizedTo`);
`sound == "custom"` with an absent `customPath` yields Classic Wood and keeps `customName` so the
Custom entry still shows the name as unavailable.

**Tests**: `MetronomePreferencesSnapshotTest` (defaults, round trip, every decoding rule),
`FakeMetronomePreferencesTest`, `OkioCustomClickSoundStoreTest` (`commonTest` with
`okio.fakefilesystem.FakeFileSystem`: staging then commit replaces the previous file atomically,
commit without staging returns false, delete is idempotent); `DataStoreMetronomePreferencesTest`
and `CustomClickSoundStoreFileTest` in `androidDeviceTest` and `iosTest` (write, read back, reopen
the file and read again; a real 1 KB file committed and found by the engine's loader path).
`DataStoreTunerPreferencesTest` still passes after the shared-instance refactor.
