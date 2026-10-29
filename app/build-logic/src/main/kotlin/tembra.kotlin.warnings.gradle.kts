// Convention plugin applied by every convention that compiles Kotlin. "No new compiler warnings" is part of the
// constitution's definition of done; CI passes `-Ptembra.warningsAsErrors=true` so a warning fails the build there,
// which keeps an automerged dependency update from landing a deprecation that Tembra still calls. Local builds keep
// warnings visible without failing.
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

if (providers.gradleProperty("tembra.warningsAsErrors").orNull == "true") {
    tasks.withType<KotlinCompilationTask<*>>().configureEach {
        compilerOptions.allWarningsAsErrors.set(true)
    }
}
