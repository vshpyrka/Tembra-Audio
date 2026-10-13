// Convention plugin for shared modules whose types are @Serializable (feature 009: the project document in
// :shared:core, its codec and the library index in :shared:data). It only applies the kotlinx.serialization compiler
// plugin from build-logic's classpath, so it shares the Kotlin Gradle plugin's classloader; the runtime artefact is
// each module's own dependency.
plugins {
    id("tembra.kmp.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}
