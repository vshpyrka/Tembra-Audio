package audio.tembra.data

/** Platform directories every persistence feature writes into. Paths are absolute and exist. */
interface AppDirectories {
    /** User-visible, backed-up storage for project folders. */
    val documents: String

    /** Regenerable data (waveform peak caches); the OS may purge it. */
    val cache: String
}
