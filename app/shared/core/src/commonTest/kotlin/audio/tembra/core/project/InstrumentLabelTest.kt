package audio.tembra.core.project

import kotlin.test.Test
import kotlin.test.assertEquals

/** Exactly the twelve labels of spec FR-021, in that order. */
class InstrumentLabelTest {

    @Test
    fun twelveLabelsInTheSpecOrder() {
        assertEquals(
            listOf("Vocals", "Guitar", "Bass", "Drums", "Percussion", "Keys", "Synth", "Strings", "Brass", "Woodwind", "Loops", "Other"),
            InstrumentLabel.entries.map { it.displayName },
        )
    }

    @Test
    fun serialNamesAreLowerCaseAndUnknownOnesReadAsOther() {
        assertEquals("drums", InstrumentLabel.Drums.serialName)
        assertEquals(InstrumentLabel.Drums, InstrumentLabel.fromSerialName("drums"))
        assertEquals(InstrumentLabel.Other, InstrumentLabel.fromSerialName("theremin"))
    }
}
