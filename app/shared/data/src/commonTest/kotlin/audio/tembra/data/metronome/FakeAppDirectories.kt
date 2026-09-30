package audio.tembra.data.metronome

import audio.tembra.data.AppDirectories

/** Fixed absolute directories for tests over a fake file system. */
internal class FakeAppDirectories(
    override val documents: String = "/documents",
    override val cache: String = "/cache",
    override val preferences: String = "/preferences",
) : AppDirectories
