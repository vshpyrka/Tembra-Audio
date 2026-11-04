package audio.tembra.engine

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The channels an arm may open on iOS (T098): the route's input channels bound the caller's maximum, because the RemoteIO
 * hardware format on bus 1 reads as a 2-channel default until the unit has run once with its input enabled, and a client
 * format wider than the route records silence in the extra channel. An unknown route (0 channels) leaves the maximum.
 */
class InputChannelLimitTest {
    @Test
    fun theRoutesChannelsBoundTheMaximum() {
        assertEquals(1, inputChannelLimit(maxChannels = 2, routeChannels = 1))
        assertEquals(2, inputChannelLimit(maxChannels = 2, routeChannels = 2))
        assertEquals(1, inputChannelLimit(maxChannels = 1, routeChannels = 2))
    }

    @Test
    fun anUnknownRouteKeepsTheMaximum() {
        assertEquals(2, inputChannelLimit(maxChannels = 2, routeChannels = 0))
        assertEquals(2, inputChannelLimit(maxChannels = 2, routeChannels = -1))
    }
}
