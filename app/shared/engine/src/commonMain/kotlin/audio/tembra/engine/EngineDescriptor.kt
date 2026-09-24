package audio.tembra.engine

import audio.tembra.core.version.SemanticVersion

/** What the native engine reports about itself (data-model.md §3). */
data class EngineDescriptor(
    /** Exactly `Tembra Engine` for the real engine. */
    val name: String,
    val version: SemanticVersion,
    /** `"<library> <version>"` entries compiled into the engine; empty on the fake. */
    val compiledLibraries: List<String>,
    /** The exact string returned by the C ABI. */
    val rawDescription: String,
)
