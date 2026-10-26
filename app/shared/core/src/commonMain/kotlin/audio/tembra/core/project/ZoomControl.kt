package audio.tembra.core.project

/**
 * The timeline's always-visible zoom control (spec FR-015, design iteration D1 of 2026-10-26): [Buttons], "−" and "+"
 * at the ruler's right end with the level between them (the default), or [OverviewStrip], the whole project above the
 * transport bar with a draggable window for the visible span. Chosen per device and remembered on the device, never in
 * the document or the per-project view state. Both exist for the owner's comparison; the one not chosen is removed
 * afterwards (T144). [id] is the stored text.
 */
enum class ZoomControl(val id: String) {
    Buttons("buttons"),
    OverviewStrip("overviewStrip");

    companion object {
        val Default: ZoomControl = Buttons

        fun byId(id: String): ZoomControl? = entries.firstOrNull { it.id == id }
    }
}
