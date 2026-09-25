package audio.tembra.ui.devtools

/** What the platform shell asked to show instead of the navigation shell: null in release builds and without an argument. */
data class DeveloperViewRequest(val view: DeveloperView?) {
    companion object {
        /** Maps a launch-argument value ("token-preview") to a request; unknown values and null map to no view. */
        fun parse(value: String?): DeveloperViewRequest =
            DeveloperViewRequest(DeveloperView.entries.firstOrNull { it.argument == value })
    }
}
