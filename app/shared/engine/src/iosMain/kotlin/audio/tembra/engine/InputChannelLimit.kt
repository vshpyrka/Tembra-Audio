package audio.tembra.engine

/**
 * The channels an arm may open (feature 010, T098): [maxChannels] bounded by the active route's [routeChannels]
 * (`AVAudioSession.inputNumberOfChannels` after the session is active for recording). RemoteIO's hardware format on
 * its input bus reads as a 2-channel default until the unit has run once with the input enabled, so the device cannot
 * be trusted to narrow a wider client format on the first arm, and the take would carry a silent channel. A route that
 * reports no channels (unknown, no input yet) leaves [maxChannels] to the device's own clamp.
 */
internal fun inputChannelLimit(maxChannels: Int, routeChannels: Long): Int =
    if (routeChannels < 1L) maxChannels else minOf(maxChannels, routeChannels.toInt()).coerceAtLeast(1)
