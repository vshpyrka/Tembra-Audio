package audio.tembra.data.timeline

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The timeline's device preferences over the shared Preferences file: the overview strip (spec FR-015; T144, T151)
 * shown while nothing is stored, the written value read back under `timeline.overviewStrip`, and the D1 comparison's
 * `timeline.zoomControl` removed by the first write; input monitoring (feature 010, contracts/take-storage.md) off
 * while nothing is stored under `timeline.monitorInput`. The file is an in-memory [DataStore]: the round trip through
 * a real file is the library's, covered by the tuner and metronome device tests.
 */
class DataStoreTimelinePreferencesTest {

    /** A [DataStore] over one value in memory, applying each transform in turn as the real one does. */
    private class InMemoryPreferences(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
        private val state = MutableStateFlow(initial)
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    @Test
    fun theStripShowsWhileNothingIsStoredAndAWriteReadsBack() = runTest {
        val store = InMemoryPreferences()
        val preferences = DataStoreTimelinePreferences(store)
        assertTrue(preferences.overviewStripShown.first(), "the strip shows by default")
        preferences.setOverviewStripShown(false)
        assertFalse(preferences.overviewStripShown.first())
        assertEquals(false, store.data.first()[booleanPreferencesKey("timeline.overviewStrip")], "the key is timeline.overviewStrip")
        preferences.setOverviewStripShown(true)
        assertTrue(preferences.overviewStripShown.first())
        assertEquals("timeline.overviewStrip", TimelinePreferenceKeys.OVERVIEW_STRIP)
    }

    @Test
    fun aWriteRemovesTheComparisonsZoomControlKey() = runTest {
        val zoomControl = stringPreferencesKey("timeline.zoomControl")
        val metronomeTempo = stringPreferencesKey("metronome.tempo")
        val store = InMemoryPreferences()
        store.edit {
            it[zoomControl] = "buttons"
            it[metronomeTempo] = "90"
        }
        val preferences = DataStoreTimelinePreferences(store)
        assertTrue(preferences.overviewStripShown.first(), "the stale choice is not read: the strip shows")
        preferences.setOverviewStripShown(true)
        val written = store.data.first()
        assertNull(written[zoomControl], "the D1 comparison's key is gone after the first write")
        assertEquals("90", written[metronomeTempo], "the other features' keys in the shared file stay")
        assertEquals(true, written[booleanPreferencesKey(TimelinePreferenceKeys.OVERVIEW_STRIP)])
    }

    @Test
    fun monitoringIsOffWhileNothingIsStoredAndAWriteReadsBack() = runTest {
        val store = InMemoryPreferences()
        val preferences = DataStoreTimelinePreferences(store)
        assertFalse(preferences.monitorInput.first(), "monitoring is off by default")
        preferences.setMonitorInput(true)
        assertTrue(preferences.monitorInput.first())
        assertEquals(true, store.data.first()[booleanPreferencesKey("timeline.monitorInput")], "the key is timeline.monitorInput")
        assertNull(store.data.first()[booleanPreferencesKey("timeline.overviewStrip")], "the strip's key is untouched")
        assertTrue(preferences.overviewStripShown.first(), "the strip still shows")
        preferences.setMonitorInput(false)
        assertFalse(preferences.monitorInput.first())
        assertEquals("timeline.monitorInput", TimelinePreferenceKeys.MONITOR_INPUT)
    }

    @Test
    fun theFakeMirrorsBothPreferences() = runTest {
        val fake = FakeTimelinePreferences()
        assertTrue(fake.overviewStripShown.first())
        assertFalse(fake.monitorInput.first(), "monitoring is off by default in the fake too")
        fake.setMonitorInput(true)
        assertTrue(fake.monitorInput.first())
        assertTrue(fake.storedMonitorInput)
        assertTrue(fake.stored, "the strip is untouched")
        assertTrue(FakeTimelinePreferences(initialMonitorInput = true).monitorInput.first())
    }
}
