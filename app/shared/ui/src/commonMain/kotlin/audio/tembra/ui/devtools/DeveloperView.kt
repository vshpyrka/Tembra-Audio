package audio.tembra.ui.devtools

/** Developer-only views, reachable solely through the debug launch argument whose value is [argument] (spec FR-022). */
enum class DeveloperView(val argument: String) {
    TokenPreview("token-preview"),
}
