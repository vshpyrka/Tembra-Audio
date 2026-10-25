package audio.tembra.ui.navigation

import audio.tembra.ui.theme.TembraIcon

/**
 * The five places a user can go (data-model.md §1). Declaration order is display order (spec FR-002) and the first
 * entry is the start destination; the shell reads this registry and never `when`s over destinations for layout.
 * [placeholderMessage] is the one sentence FR-011 and FR-013 require, kept as data so tests can assert the exact text.
 */
enum class Destination(
    val id: String,
    val label: String,
    val title: String,
    val icon: TembraIcon,
    val hasSupportingPane: Boolean,
    val placeholderMessage: String,
) {
    Timeline("timeline", "Timeline", "Timeline", TembraIcon.Timeline, hasSupportingPane = false, "The timeline arranger arrives with the transport and timeline feature."),
    Tuner("tuner", "Tuner", "Tuner", TembraIcon.Tuner, hasSupportingPane = false, "The instrument tuner arrives with the tuner feature."),
    Metronome("metronome", "Metronome", "Metronome", TembraIcon.Metronome, hasSupportingPane = false, "The metronome arrives with the metronome feature."),
    Projects("projects", "Projects", "Projects", TembraIcon.Projects, hasSupportingPane = false, "Your projects and library arrive with the projects feature."),
    Profile("profile", "Profile", "Profile", TembraIcon.Profile, hasSupportingPane = false, "Your profile and sign-in arrive with the collaboration feature."),
    ;

    /**
     * Whether the destination shows the transport bar and so keeps the engine's transport (constitution v1.3.2: the
     * Timeline, and Effects and Pads once they exist; never Tuner, Metronome, Projects or Profile, whose audio needs
     * an idle engine).
     */
    val hostsTransport: Boolean get() = this == Timeline
}
