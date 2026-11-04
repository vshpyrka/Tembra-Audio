package audio.tembra.core.project

import audio.tembra.core.project.ProjectFixtures.clip
import audio.tembra.core.project.ProjectFixtures.track
import kotlin.test.Test
import kotlin.test.assertEquals

/** "Take N" naming on a track (feature 010, spec FR-012): one more than the highest "Take N" clip name, starting at 1. */
class TrackNextTakeNameTest {

    @Test
    fun anEmptyTrackStartsAtTakeOne() {
        assertEquals("Take 1", track(1).nextTakeName())
    }

    @Test
    fun oneMoreThanTheHighestTakeNumberWhateverTheOrder() {
        assertEquals("Take 2", track(1, clip(1, 0.0, name = "Take 1")).nextTakeName())
        assertEquals("Take 8", track(1, clip(1, 0.0, name = "Take 7"), clip(2, 70.0, name = "loop"), clip(3, 140.0, name = "Take 3")).nextTakeName())
        assertEquals("Take 11", track(1, clip(1, 0.0, name = "Take 10")).nextTakeName())
    }

    @Test
    fun takeOneWhenOnlyOtherNamesExist() {
        assertEquals("Take 1", track(1, clip(1, 0.0, name = "loop"), clip(2, 70.0, name = "Take"), clip(3, 140.0, name = "Take x"), clip(4, 210.0, name = "Take 2b"), clip(5, 280.0, name = "take 4")).nextTakeName())
    }

    @Test
    fun gapsAreNotFilled() {
        assertEquals("Take 6", track(1, clip(1, 0.0, name = "Take 5"), clip(2, 70.0, name = "Take 2")).nextTakeName())
    }
}
